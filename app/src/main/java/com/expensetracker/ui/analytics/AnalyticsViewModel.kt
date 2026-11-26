package com.expensetracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.domain.Analytics
import com.expensetracker.domain.ChartPoint
import com.expensetracker.domain.Period
import com.expensetracker.domain.PeriodSummary
import com.expensetracker.domain.PeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class AnalyticsUiState(
    val loading: Boolean = true,
    val currency: AppCurrency = AppCurrency.USD,
    val type: PeriodType = PeriodType.MONTH,
    val offset: Int = 0,
    val today: LocalDate = LocalDate.now(),
    val summary: PeriodSummary? = null,
    val trend: List<ChartPoint> = emptyList(),
) {
    val canGoNext: Boolean get() = offset < 0

    val periodLabel: String
        get() {
            val period = summary?.period ?: return ""
            return when (type) {
                PeriodType.WEEK -> {
                    val sameMonth = period.start.month == period.end.month
                    val startText = period.start.format(DateTimeFormatter.ofPattern("MMM d"))
                    val endText = period.end.format(DateTimeFormatter.ofPattern(if (sameMonth) "d" else "MMM d"))
                    val year = if (period.end.year != today.year) ", ${period.end.year}" else ""
                    "$startText – $endText$year"
                }
                PeriodType.MONTH -> period.start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                PeriodType.YEAR -> period.start.year.toString()
            }
        }

    val comparedTo: String
        get() = when (type) {
            PeriodType.WEEK -> "previous week"
            PeriodType.MONTH -> "previous month"
            PeriodType.YEAR -> "previous year"
        }

    /** Index of today's column in the time series, when today falls inside the selected period. */
    val todayIndex: Int?
        get() {
            val period = summary?.period ?: return null
            if (today !in period) return null
            return when (type) {
                PeriodType.WEEK, PeriodType.MONTH -> (today.toEpochDay() - period.start.toEpochDay()).toInt()
                PeriodType.YEAR -> today.monthValue - 1
            }
        }
}

class AnalyticsViewModel(
    expenses: ExpenseRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val type = MutableStateFlow(PeriodType.MONTH)
    private val offset = MutableStateFlow(0)

    val uiState: StateFlow<AnalyticsUiState> =
        combine(expenses.expenses, settings.settings, type, offset) { all, prefs, type, offset ->
            val today = LocalDate.now()
            val period = Period.of(type, today, offset)
            val trendEnd = minOf(YearMonth.from(period.end), YearMonth.from(today))
            AnalyticsUiState(
                loading = false,
                currency = prefs.currency,
                type = type,
                offset = offset,
                today = today,
                summary = Analytics.summarize(all, period, today),
                trend = Analytics.monthlySeries(all, trendEnd, 6),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun setType(value: PeriodType) {
        if (type.value != value) {
            type.value = value
            offset.value = 0
        }
    }

    fun previous() = offset.update { it - 1 }

    fun next() = offset.update { if (it < 0) it + 1 else it }
}
