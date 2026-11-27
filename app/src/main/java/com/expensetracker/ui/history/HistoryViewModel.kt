package com.expensetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
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

data class DayGroup(val date: LocalDate, val spent: Double, val received: Double, val items: List<Expense>)

private data class Filters(
    val month: YearMonth = YearMonth.now(),
    val query: String = "",
    val category: Category? = null,
    val type: EntryType? = null,
    /** Category names as shown on screen, so a Khmer search finds Khmer category names. */
    val names: Map<Category, String> = emptyMap(),
)

data class HistoryUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val query: String = "",
    val category: Category? = null,
    val type: EntryType? = null,
    val groups: List<DayGroup> = emptyList(),
    val spent: Double = 0.0,
    val received: Double = 0.0,
    val count: Int = 0,
    val currency: AppCurrency = AppCurrency.USD,
) {
    val canGoNext: Boolean get() = month < YearMonth.now()
    val isFiltered: Boolean get() = query.isNotBlank() || category != null || type != null
    val searchingAllMonths: Boolean get() = query.isNotBlank()
}

class HistoryViewModel(
    private val expenses: ExpenseRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(Filters())
    private var lastDeleted: Expense? = null

    val uiState: StateFlow<HistoryUiState> =
        combine(expenses.expenses, settings.settings, filters) { all, prefs, f ->
            val (month, query, category, type) = f
            val needle = query.trim()
            // A search looks through every month; otherwise show the selected month.
            val filtered = all.filter { expense ->
                (needle.isNotEmpty() || YearMonth.from(expense.date) == month) &&
                    (type == null || expense.type == type) &&
                    (category == null || expense.category == category) &&
                    (needle.isEmpty() || expense.note.contains(needle, ignoreCase = true) ||
                        expense.category.label.contains(needle, ignoreCase = true) ||
                        f.names[expense.category]?.contains(needle, ignoreCase = true) == true)
            }
            HistoryUiState(
                loading = false,
                month = month,
                query = query,
                category = category,
                type = type,
                groups = filtered.groupBy { it.date }
                    .map { (date, items) ->
                        DayGroup(date, items.filterNot { it.isIncome }.sumOf { it.amount }, items.filter { it.isIncome }.sumOf { it.amount }, items)
                    }
                    .sortedByDescending { it.date },
                spent = filtered.filterNot { it.isIncome }.sumOf { it.amount },
                received = filtered.filter { it.isIncome }.sumOf { it.amount },
                count = filtered.size,
                currency = prefs.currency,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousMonth() = filters.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = filters.update { if (it.month < YearMonth.now()) it.copy(month = it.month.plusMonths(1)) else it }

    fun setQuery(value: String) = filters.update { it.copy(query = value) }

    fun setCategory(value: Category?) = filters.update { it.copy(category = value) }

    fun setCategoryNames(names: Map<Category, String>) = filters.update { if (it.names == names) it else it.copy(names = names) }

    /** Spending only, income only, or both (null); clears a category that doesn't fit. */
    fun setType(value: EntryType?) = filters.update {
        it.copy(type = value, category = it.category?.takeIf { c -> value == null || c.type == value })
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
