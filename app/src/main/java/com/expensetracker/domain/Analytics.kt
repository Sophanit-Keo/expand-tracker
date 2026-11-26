package com.expensetracker.domain

import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodType(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
}

data class Period(val type: PeriodType, val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** The same kind of period immediately before this one. */
    fun previous(): Period = when (type) {
        PeriodType.WEEK -> Period(type, start.minusWeeks(1), end.minusWeeks(1))
        PeriodType.MONTH -> YearMonth.from(start).minusMonths(1).let { Period(type, it.atDay(1), it.atEndOfMonth()) }
        PeriodType.YEAR -> Period(type, start.minusYears(1), end.minusYears(1))
    }

    /** Number of days that count toward an average: the whole period, or only up to [today]. */
    fun elapsedDays(today: LocalDate): Long {
        if (today.isBefore(start)) return 0
        val last = if (today.isBefore(end)) today else end
        return ChronoUnit.DAYS.between(start, last) + 1
    }

    companion object {
        /** The period of [type] containing [today], shifted by [offset] periods (negative = past). */
        fun of(type: PeriodType, today: LocalDate, offset: Int = 0): Period = when (type) {
            PeriodType.WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
                Period(type, start, start.plusDays(6))
            }
            PeriodType.MONTH -> YearMonth.from(today).plusMonths(offset.toLong())
                .let { Period(type, it.atDay(1), it.atEndOfMonth()) }
            PeriodType.YEAR -> LocalDate.of(today.year + offset, 1, 1)
                .let { Period(type, it, it.withDayOfYear(it.lengthOfYear())) }
        }
    }
}

data class ChartPoint(val label: String, val value: Double)

data class CategoryTotal(val category: Category, val total: Double, val share: Float, val count: Int)

data class PeriodSummary(
    val period: Period,
    val total: Double,
    val previousTotal: Double,
    val count: Int,
    val dailyAverage: Double,
    val byCategory: List<CategoryTotal>,
    /** Per-day totals for a week or month; per-month totals for a year. */
    val series: List<ChartPoint>,
    val largest: Expense?,
    /** Weekday with the highest total spending in the period. */
    val topWeekday: DayOfWeek?,
) {
    /** Percentage change versus the previous period, or null if there is nothing to compare with. */
    val changePercent: Double?
        get() = if (previousTotal > 0) (total - previousTotal) / previousTotal * 100 else null
}

object Analytics {
    fun total(expenses: Iterable<Expense>): Double = expenses.sumOf { it.amount }

    fun inRange(expenses: List<Expense>, start: LocalDate, end: LocalDate): List<Expense> =
        expenses.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }

    fun byCategory(expenses: List<Expense>): List<CategoryTotal> {
        val grandTotal = total(expenses)
        if (grandTotal <= 0) return emptyList()
        return expenses.groupBy { it.category }
            .map { (category, items) ->
                val sum = total(items)
                CategoryTotal(category, sum, (sum / grandTotal).toFloat(), items.size)
            }
            .sortedByDescending { it.total }
    }

    /** One point per day from [start] to [end], labelled with [label]. */
    fun dailySeries(
        expenses: List<Expense>,
        start: LocalDate,
        end: LocalDate,
        label: (LocalDate) -> String,
    ): List<ChartPoint> {
        val totals = expenses.groupBy { it.date }.mapValues { (_, items) -> total(items) }
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { ChartPoint(label(it), totals[it] ?: 0.0) }
            .toList()
    }

    /** Totals for the [count] months ending with [lastMonth], oldest first. */
    fun monthlySeries(expenses: List<Expense>, lastMonth: YearMonth, count: Int, locale: Locale = Locale.getDefault()): List<ChartPoint> {
        val totals = expenses.groupBy { YearMonth.from(it.date) }.mapValues { (_, items) -> total(items) }
        return (count - 1 downTo 0).map { back ->
            val month = lastMonth.minusMonths(back.toLong())
            ChartPoint(month.month.getDisplayName(TextStyle.SHORT, locale), totals[month] ?: 0.0)
        }
    }

    fun summarize(
        expenses: List<Expense>,
        period: Period,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): PeriodSummary {
        val current = inRange(expenses, period.start, period.end)
        val previous = period.previous()
        val total = total(current)
        val elapsed = period.elapsedDays(today)

        val series = when (period.type) {
            PeriodType.WEEK -> dailySeries(current, period.start, period.end) {
                it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            }
            PeriodType.MONTH -> dailySeries(current, period.start, period.end) { it.dayOfMonth.toString() }
            PeriodType.YEAR -> monthlySeries(current, YearMonth.of(period.start.year, 12), 12, locale)
        }

        return PeriodSummary(
            period = period,
            total = total,
            previousTotal = total(inRange(expenses, previous.start, previous.end)),
            count = current.size,
            dailyAverage = if (elapsed > 0) total / elapsed else 0.0,
            byCategory = byCategory(current),
            series = series,
            largest = current.maxByOrNull { it.amount },
            topWeekday = current.groupBy { it.date.dayOfWeek }
                .maxByOrNull { (_, items) -> total(items) }
                ?.key,
        )
    }
}
