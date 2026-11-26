package com.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SampleData
import com.expensetracker.data.SettingsRepository
import com.expensetracker.domain.Analytics
import com.expensetracker.domain.CategoryTotal
import com.expensetracker.domain.ChartPoint
import com.expensetracker.domain.Period
import com.expensetracker.domain.PeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class HomeUiState(
    val loading: Boolean = true,
    val currency: AppCurrency = AppCurrency.USD,
    val today: LocalDate = LocalDate.now(),
    val monthName: String = "",
    val monthTotal: Double = 0.0,
    val previousMonthTotal: Double = 0.0,
    val budget: Double = 0.0,
    val todayTotal: Double = 0.0,
    val weekTotal: Double = 0.0,
    val last7Days: List<ChartPoint> = emptyList(),
    val topCategories: List<CategoryTotal> = emptyList(),
    val recent: List<Expense> = emptyList(),
    val isEmpty: Boolean = true,
) {
    /** Change vs the same number of days into last month, so early-month numbers aren't alarming. */
    val monthChangePercent: Double?
        get() = if (previousMonthTotal > 0) (monthTotal - previousMonthTotal) / previousMonthTotal * 100 else null

    val daysLeftInMonth: Int get() = YearMonth.from(today).lengthOfMonth() - today.dayOfMonth
}

class HomeViewModel(
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(expenses.expenses, settings.settings) { all, prefs ->
        val today = LocalDate.now()
        val month = Period.of(PeriodType.MONTH, today)
        val week = Period.of(PeriodType.WEEK, today)
        val monthItems = Analytics.inRange(all, month.start, today)
        val previousMonth = month.previous()
        // Compare like with like: the first N days of last month.
        val previousSameSpan = Analytics.inRange(
            all,
            previousMonth.start,
            minOf(previousMonth.start.plusDays((today.dayOfMonth - 1).toLong()), previousMonth.end),
        )

        HomeUiState(
            loading = false,
            currency = prefs.currency,
            today = today,
            monthName = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            monthTotal = Analytics.total(monthItems),
            previousMonthTotal = Analytics.total(previousSameSpan),
            budget = prefs.monthlyBudget,
            todayTotal = Analytics.total(all.filter { it.date == today }),
            weekTotal = Analytics.total(Analytics.inRange(all, week.start, today)),
            last7Days = Analytics.dailySeries(all, today.minusDays(6), today) {
                if (it == today) "Today" else it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            },
            topCategories = Analytics.byCategory(monthItems).take(3),
            recent = all.take(6),
            isEmpty = all.isEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun loadSampleData() {
        viewModelScope.launch {
            val currency = settings.settings.first().currency
            expenses.addAll(SampleData.generate(currency))
        }
    }
}
