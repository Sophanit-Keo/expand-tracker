package com.expensetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(val date: LocalDate, val total: Double, val items: List<Expense>)

data class HistoryUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val query: String = "",
    val category: Category? = null,
    val groups: List<DayGroup> = emptyList(),
    val total: Double = 0.0,
    val count: Int = 0,
    val currency: AppCurrency = AppCurrency.USD,
) {
    val canGoNext: Boolean get() = month < YearMonth.now()
    val isFiltered: Boolean get() = query.isNotBlank() || category != null
}

class HistoryViewModel(
    private val expenses: ExpenseRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<Category?>(null)
    private var lastDeleted: Expense? = null

    val uiState: StateFlow<HistoryUiState> =
        combine(expenses.expenses, settings.settings, month, query, category) { all, prefs, month, query, category ->
            val needle = query.trim()
            val filtered = all.filter { expense ->
                YearMonth.from(expense.date) == month &&
                    (category == null || expense.category == category) &&
                    (needle.isEmpty() || expense.note.contains(needle, ignoreCase = true) ||
                        expense.category.label.contains(needle, ignoreCase = true))
            }
            HistoryUiState(
                loading = false,
                month = month,
                query = query,
                category = category,
                groups = filtered.groupBy { it.date }
                    .map { (date, items) -> DayGroup(date, items.sumOf { it.amount }, items) }
                    .sortedByDescending { it.date },
                total = filtered.sumOf { it.amount },
                count = filtered.size,
                currency = prefs.currency,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(value: Category?) {
        category.value = value
    }

    fun delete(expense: Expense) {
        lastDeleted = expense
        viewModelScope.launch { expenses.delete(expense) }
    }

    fun undoDelete() {
        val expense = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { expenses.save(expense) }
    }
}
