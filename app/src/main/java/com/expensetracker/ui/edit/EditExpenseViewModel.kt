package com.expensetracker.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.util.amountToInput
import com.expensetracker.util.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditUiState(
    val isEditing: Boolean = false,
    val loading: Boolean = true,
    val currency: AppCurrency = AppCurrency.USD,
    val amountText: String = "",
    val category: Category = Category.FOOD,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val showAmountError: Boolean = false,
    val done: Boolean = false,
) {
    val amount: Double? get() = amountText.toDoubleOrNull()?.takeIf { it > 0 }
}

class EditExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val expenseId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var original: Expense? = null

    private val _state = MutableStateFlow(EditUiState(isEditing = expenseId != null))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = settings.settings.first().currency
            val existing = expenseId?.let { expenses.get(it) }
            original = existing
            _state.update {
                if (existing != null) {
                    it.copy(
                        loading = false,
                        currency = currency,
                        amountText = amountToInput(existing.amount),
                        category = existing.category,
                        date = existing.date,
                        note = existing.note,
                    )
                } else {
                    it.copy(loading = false, currency = currency, isEditing = false)
                }
            }
        }
    }

    fun onAmountChange(text: String) = _state.update {
        it.copy(amountText = sanitizeAmountInput(text, it.currency.decimals), showAmountError = false)
    }

    fun onCategoryChange(category: Category) = _state.update { it.copy(category = category) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date) }

    fun onNoteChange(note: String) = _state.update { it.copy(note = note.take(80)) }

    fun save() {
        val current = _state.value
        val amount = current.amount
        if (amount == null) {
            _state.update { it.copy(showAmountError = true) }
            return
        }
        viewModelScope.launch {
            val expense = (original ?: Expense(amount = amount, category = current.category, date = current.date)).copy(
                amount = amount,
                category = current.category,
                date = current.date,
                note = current.note.trim(),
            )
            expenses.save(expense)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val expense = original ?: return
        viewModelScope.launch {
            expenses.delete(expense)
            _state.update { it.copy(done = true) }
        }
    }
}
