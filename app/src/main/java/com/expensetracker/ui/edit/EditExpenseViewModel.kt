package com.expensetracker.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.importer.CategoryGuesser
import com.expensetracker.ui.AppMessage
import com.expensetracker.ui.AppMessages
import com.expensetracker.ui.Prefill
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

/** Something you enter often: one tap fills in note, category and amount. */
data class QuickFill(val note: String, val category: Category, val amount: Double, val count: Int)

data class EditUiState(
    val isEditing: Boolean = false,
    val type: EntryType = EntryType.EXPENSE,
    val loading: Boolean = true,
    val currency: AppCurrency = AppCurrency.USD,
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
    val amount: Double? get() = amountText.toDoubleOrNull()?.takeIf { it > 0 }
}

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
        Prefill(amount, category, savedStateHandle.get<String>("note").orEmpty())
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
            val currency = settings.settings.first().currency
            val existing = expenseId?.let { expenses.get(it) }
            original = existing
            _state.update {
                when {
                    existing != null -> it.copy(
                        loading = false,
                        currency = currency,
                        amountText = amountToInput(existing.amount),
                        type = existing.type,
                        category = existing.category,
                        date = existing.date,
                        note = existing.note,
                    )
                    prefill != null -> it.copy(
                        loading = false,
                        currency = currency,
                        isEditing = false,
                        amountText = amountToInput(prefill.amount),
                        note = prefill.note,
                    )
                    else -> it.copy(loading = false, currency = currency, isEditing = false)
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
                QuickFill(latest.note.trim(), latest.category, latest.amount, items.size)
            }
            .sortedWith(compareByDescending<QuickFill> { it.count }.thenBy { it.note })
            .take(6)
            .toList()

    fun onAmountChange(text: String) = _state.update {
        it.copy(amountText = sanitizeAmountInput(text, it.currency.decimals), showAmountError = false)
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
            it.copy(
                note = fill.note,
                category = fill.category,
                categoryAuto = false,
                amountText = amountToInput(fill.amount),
                showAmountError = false,
            )
        }
    }

    fun duplicate(): Prefill? {
        val s = _state.value
        val amount = s.amount ?: return null
        return Prefill(amount, s.category, s.note)
    }

    fun save() {
        val current = _state.value
        val amount = current.amount
        if (amount == null) {
            _state.update { it.copy(showAmountError = true) }
            return
        }
        viewModelScope.launch {
            val before = original
            val expense = (before ?: Expense(amount = amount, category = current.category, date = current.date)).copy(
                amount = amount,
                category = current.category,
                type = current.category.type,
                date = current.date,
                note = current.note.trim(),
            )
            val signed = (if (expense.isIncome) "+" else "−") + formatMoney(amount, current.currency)
            if (before == null) {
                val id = expenses.add(expense)
                AppMessages.post(AppMessage("Saved “${expense.title}” · $signed", "Undo") { expenses.delete(expense.copy(id = id)) })
            } else {
                expenses.save(expense)
                AppMessages.post(AppMessage("Changes saved", "Undo") { expenses.save(before) })
            }
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val expense = original ?: return
        viewModelScope.launch {
            expenses.delete(expense)
            AppMessages.post(AppMessage("Deleted “${expense.title}”", "Undo") { expenses.save(expense) })
            _state.update { it.copy(done = true) }
        }
    }
}
