package com.expensetracker.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.domain.Exchange
import com.expensetracker.importer.CategoryGuesser
import com.expensetracker.ui.AppMessage
import com.expensetracker.ui.AppMessages
import com.expensetracker.ui.Prefill
import com.expensetracker.ui.UiText
import com.expensetracker.ui.titleText
import com.expensetracker.util.amountToInput
import com.expensetracker.util.formatMoney
import com.expensetracker.util.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Something you enter often: one tap fills in note, category and amount (in [currency], or the main one). */
data class QuickFill(val note: String, val category: Category, val amount: Double, val count: Int, val currency: AppCurrency? = null)

data class EditUiState(
    val isEditing: Boolean = false,
    val type: EntryType = EntryType.EXPENSE,
    val loading: Boolean = true,
    /** The main currency: totals are kept in it. */
    val currency: AppCurrency = AppCurrency.USD,
    /** The currency the amount is typed in. */
    val entryCurrency: AppCurrency = AppCurrency.USD,
    /** Saved exchange rates (main per one unit of each currency). */
    val rates: Map<AppCurrency, Double> = emptyMap(),
    /** The rate as typed, the way it's written: the number in "1 USD = [4100] ៛". */
    val rateText: String = "",
    val showRateError: Boolean = false,
    val amountText: String = "",
    val category: Category = Category.FOOD,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val showAmountError: Boolean = false,
    val done: Boolean = false,
    /** The category was picked from the note, not by the user. */
    val categoryAuto: Boolean = false,
    val suggestions: List<QuickFill> = emptyList(),
) {
    /** The amount typed, in [entryCurrency]. */
    val amount: Double? get() = amountText.toDoubleOrNull()?.takeIf { it > 0 }

    val isForeign: Boolean get() = entryCurrency != currency

    /** Main-currency units per one unit of [entryCurrency]. */
    val rate: Double?
        get() = if (!isForeign) 1.0 else rateText.toDoubleOrNull()?.let { Exchange.rateFromQuote(entryCurrency, currency, it) }

    /** The amount in the main currency: what totals and charts count. */
    val mainAmount: Double? get() = amount?.let { a -> rate?.let { a * it } }

    /** "1 [quoteOne] = x [quoteOther]": the currency worth more comes first. */
    val quoteOne: AppCurrency get() = if (Exchange.foreignFirst(entryCurrency, currency)) entryCurrency else currency
    val quoteOther: AppCurrency get() = if (quoteOne == entryCurrency) currency else entryCurrency
}

/** The typed rate for [currency]: the saved one, else empty (the field shows a rough example). */
private fun rateInput(currency: AppCurrency, main: AppCurrency, rate: Double?): String =
    rate?.takeIf { currency != main }?.let { Exchange.rateToInput(Exchange.quote(currency, main, it).value) }.orEmpty()

/** Keeps a typed amount when the currency changes, trimmed to the new currency's decimals. */
private fun amountFor(text: String, currency: AppCurrency): String =
    if (currency.decimals == 0) text.substringBefore('.') else sanitizeAmountInput(text, currency.decimals)

class EditExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val expenseId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val requestedType: EntryType =
        EntryType.entries.firstOrNull { it.name == savedStateHandle.get<String>("type") } ?: EntryType.EXPENSE
    private val prefill: Prefill? = run {
        val amount = savedStateHandle.get<String>("amount")?.toDoubleOrNull() ?: return@run null
        val category = Category.entries.firstOrNull { it.name == savedStateHandle.get<String>("category") } ?: return@run null
        Prefill(
            amount,
            category,
            savedStateHandle.get<String>("note").orEmpty(),
            AppCurrency.entries.firstOrNull { it.name == savedStateHandle.get<String>("currency") },
            savedStateHandle.get<String>("rate")?.toDoubleOrNull(),
        )
    }
    private var original: Expense? = null
    private var history: List<Expense> = emptyList()
    private var learned: Map<String, Category> = emptyMap()
    /** The user chose a category themselves: stop guessing from the note. */
    private var categoryTouched = prefill != null

    private val _state = MutableStateFlow(
        EditUiState(isEditing = expenseId != null, type = requestedType, category = prefill?.category ?: Category.default(requestedType)),
    )
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = settings.settings.first()
            val currency = prefs.currency
            val existing = expenseId?.let { expenses.get(it) }
            original = existing
            _state.update {
                val base = it.copy(loading = false, currency = currency, entryCurrency = currency, rates = prefs.rates)
                when {
                    existing != null -> {
                        val foreign = existing.foreign
                        base.copy(
                            entryCurrency = foreign?.second ?: currency,
                            amountText = amountToInput(foreign?.first ?: existing.amount),
                            rateText = foreign?.let { (_, c) -> rateInput(c, currency, existing.exchangeRate) }.orEmpty(),
                            type = existing.type,
                            category = existing.category,
                            date = existing.date,
                            note = existing.note,
                        )
                    }
                    prefill != null -> {
                        val entryCurrency = prefill.currency ?: currency
                        base.copy(
                            isEditing = false,
                            entryCurrency = entryCurrency,
                            amountText = amountToInput(prefill.amount),
                            rateText = rateInput(entryCurrency, currency, prefill.rate ?: prefs.rates[entryCurrency]),
                            note = prefill.note,
                        )
                    }
                    else -> base.copy(isEditing = false)
                }
            }
            // Ready to type already; now load what quick fill and category guesses learn from.
            history = expenses.getAll()
            learned = expenses.categoryRules()
            if (existing == null) _state.update { it.copy(suggestions = suggestionsFor(it.type)) }
        }
    }

    /** Most frequent (note, category) pairs of this type, most used first, with their latest amount. */
    private fun suggestionsFor(type: EntryType): List<QuickFill> =
        history.asSequence()
            .filter { it.type == type && it.note.isNotBlank() && !it.isSample }
            .groupBy { it.note.trim().lowercase() to it.category }
            .filter { (_, items) -> items.size >= 2 }
            .map { (_, items) ->
                val latest = items.maxBy { it.date }
                val foreign = latest.foreign?.takeIf { (_, c) -> c != _state.value.currency }
                QuickFill(latest.note.trim(), latest.category, foreign?.first ?: latest.amount, items.size, foreign?.second)
            }
            .sortedWith(compareByDescending<QuickFill> { it.count }.thenBy { it.note })
            .take(6)
            .toList()

    fun onAmountChange(text: String) = _state.update {
        it.copy(amountText = sanitizeAmountInput(text, it.entryCurrency.decimals), showAmountError = false)
    }

    /** Typing the amount in another currency: the saved rate for it is filled in, and can be changed. */
    fun onCurrencyChange(currency: AppCurrency) = _state.update {
        if (it.entryCurrency == currency) return@update it
        it.copy(
            entryCurrency = currency,
            amountText = amountFor(it.amountText, currency),
            rateText = rateInput(currency, it.currency, it.rates[currency]),
            showRateError = false,
        )
    }

    fun onRateChange(text: String) = _state.update {
        it.copy(rateText = sanitizeAmountInput(text, 6, maxLength = 14), showRateError = false)
    }

    fun onCategoryChange(category: Category) {
        categoryTouched = true
        _state.update { it.copy(category = category, categoryAuto = false) }
    }

    fun onTypeChange(type: EntryType) = _state.update {
        if (it.type == type) {
            it
        } else {
            categoryTouched = false
            it.copy(type = type, category = guessCategory(it.note, type) ?: Category.default(type), suggestions = suggestionsFor(type))
        }
    }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date) }

    fun onNoteChange(note: String) = _state.update {
        val text = note.take(80)
        if (categoryTouched) return@update it.copy(note = text)
        val guess = guessCategory(text, it.type)
        it.copy(note = text, category = guess ?: it.category, categoryAuto = guess != null)
    }

    /**
     * Category for a note: what you used last time for the same merchant, then what you taught the
     * importer, then keywords ("coffee" → Food).
     */
    private fun guessCategory(note: String, type: EntryType): Category? {
        if (note.isBlank()) return null
        val key = CategoryGuesser.merchantKey(note)
        if (key.isNotEmpty()) {
            history.filter { it.type == type && !it.isSample && CategoryGuesser.merchantKey(it.note) == key }
                .maxByOrNull { it.date }?.let { return it.category }
            learned[key]?.takeIf { it.type == type }?.let { return it }
        }
        return if (type == EntryType.INCOME) {
            CategoryGuesser.guessIncome(note).takeIf { it != Category.OTHER_INCOME }
        } else {
            CategoryGuesser.guess(note)
        }
    }

    fun applySuggestion(fill: QuickFill) {
        categoryTouched = true
        _state.update {
            val currency = fill.currency ?: it.currency
            it.copy(
                note = fill.note,
                category = fill.category,
                categoryAuto = false,
                entryCurrency = currency,
                amountText = amountToInput(fill.amount),
                rateText = if (currency == it.entryCurrency && it.rateText.isNotEmpty()) it.rateText else rateInput(currency, it.currency, it.rates[currency]),
                showAmountError = false,
                showRateError = false,
            )
        }
    }

    fun duplicate(): Prefill? {
        val s = _state.value
        val amount = s.amount ?: return null
        return Prefill(amount, s.category, s.note, s.entryCurrency.takeIf { s.isForeign }, s.rate.takeIf { s.isForeign })
    }

    fun save() {
        val current = _state.value
        val amount = current.amount
        val rate = current.rate
        if (amount == null || rate == null) {
            _state.update { it.copy(showAmountError = amount == null, showRateError = rate == null) }
            return
        }
        viewModelScope.launch {
            val before = original
            val foreign = current.isForeign
            val expense = (before ?: Expense(amount = amount, category = current.category, date = current.date)).copy(
                amount = amount * rate,
                category = current.category,
                type = current.category.type,
                date = current.date,
                note = current.note.trim(),
                originalAmount = amount.takeIf { foreign },
                originalCurrency = current.entryCurrency.takeIf { foreign },
            )
            // The rate used becomes the one suggested next time.
            if (foreign) settings.setRate(current.entryCurrency, rate)
            val signed = (if (expense.isIncome) "+" else "−") + formatMoney(amount, current.entryCurrency)
            if (before == null) {
                val id = expenses.add(expense)
                AppMessages.post(AppMessage(UiText.Res(R.string.msg_saved, expense.titleText(), signed), UiText.Res(R.string.undo)) { expenses.delete(expense.copy(id = id)) })
            } else {
                expenses.save(expense)
                AppMessages.post(AppMessage(UiText.Res(R.string.msg_changes_saved), UiText.Res(R.string.undo)) { expenses.save(before) })
            }
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val expense = original ?: return
        viewModelScope.launch {
            expenses.delete(expense)
            AppMessages.post(AppMessage(UiText.Res(R.string.msg_deleted, expense.titleText()), UiText.Res(R.string.undo)) { expenses.save(expense) })
            _state.update { it.copy(done = true) }
        }
    }
}
