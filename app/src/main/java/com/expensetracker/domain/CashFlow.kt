package com.expensetracker.domain

import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.data.Expense
import com.expensetracker.importer.CategoryGuesser
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Money in and money out for one slot of a chart (a day or a month). */
data class CashPoint(val label: String, val cashIn: Double, val cashOut: Double) {
    val net: Double get() = cashIn - cashOut
}

/** Something that happens about once a month for a similar amount: a salary, rent, a subscription. */
data class RecurringItem(
    val name: String,
    val category: Category,
    val type: EntryType,
    val typicalAmount: Double,
    val dayOfMonth: Int,
    val monthsSeen: Int,
    /** Already happened in the current month. */
    val seenThisMonth: Boolean,
    /** When it's next expected (today if it's late this month). */
    val nextDate: LocalDate,
)

/** Month-to-date outlook; only for the current month. */
data class MonthOutlook(
    /** Days left in the month, today included. */
    val remainingDays: Int,
    /** Recurring income not received yet this month (e.g. salary later in the month). */
    val expectedIncome: Double,
    /** Recurring bills not paid yet this month. */
    val expectedBills: Double,
    /** Spending by month end at the current day-to-day pace plus the bills still to come. */
    val projectedOut: Double,
    val projectedIn: Double,
    /** What can be spent per day from now on to end the month even (negative = already short). */
    val safeToSpendPerDay: Double,
) {
    val projectedNet: Double get() = projectedIn - projectedOut
}

data class CashFlowSummary(
    val period: Period,
    val cashIn: Double,
    val cashOut: Double,
    val previousIn: Double,
    val previousOut: Double,
    /** Per day for a week or month, per month for a year. */
    val series: List<CashPoint>,
    /** Cumulative net through the period (stops at today for the current period). */
    val runningNet: List<ChartPoint>,
    val incomeBySource: List<CategoryTotal>,
    val recurringIncome: List<RecurringItem>,
    val recurringBills: List<RecurringItem>,
    /** The six months ending with this period's last month (or the current month). */
    val monthly: List<CashPoint>,
    /** Completed months among [monthly] where more went out than came in. */
    val monthsOverspent: Int,
    val completedMonthsWithData: Int,
    val outlook: MonthOutlook?,
) {
    val net: Double get() = cashIn - cashOut
    val previousNet: Double get() = previousIn - previousOut

    /** Share of income kept: net ÷ income. */
    val savingsRate: Double? get() = if (cashIn > 0) net / cashIn else null
    val previousSavingsRate: Double? get() = if (previousIn > 0) previousNet / previousIn else null

    val recurringBillsPerMonth: Double get() = recurringBills.sumOf { it.typicalAmount }

    /** Average income of the completed months in [monthly] that had income. */
    val averageMonthlyIncome: Double?
        get() = monthly.dropLast(1).map { it.cashIn }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()

    val hasIncome: Boolean get() = monthly.any { it.cashIn > 0 } || cashIn > 0
}

object CashFlow {
    fun summarize(entries: List<Expense>, period: Period, today: LocalDate, locale: Locale = Locale.getDefault()): CashFlowSummary {
        val inPeriod = Analytics.inRange(entries, period.start, period.end)
        val income = Analytics.income(inPeriod)
        val spending = Analytics.spending(inPeriod)
        val previous = period.previous()
        val previousItems = Analytics.inRange(entries, previous.start, previous.end)
        val isCurrent = today in period

        val series = when (period.type) {
            PeriodType.WEEK, PeriodType.MONTH -> {
                val inByDay = income.groupBy { it.date }.mapValues { (_, v) -> Analytics.total(v) }
                val outByDay = spending.groupBy { it.date }.mapValues { (_, v) -> Analytics.total(v) }
                generateSequence(period.start) { it.plusDays(1) }.takeWhile { !it.isAfter(period.end) }.map { day ->
                    val label = if (period.type == PeriodType.WEEK) day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) else day.dayOfMonth.toString()
                    CashPoint(label, inByDay[day] ?: 0.0, outByDay[day] ?: 0.0)
                }.toList()
            }
            PeriodType.YEAR -> (1..12).map { m ->
                val month = YearMonth.of(period.start.year, m)
                val items = inPeriod.filter { YearMonth.from(it.date) == month }
                CashPoint(
                    month.month.getDisplayName(TextStyle.SHORT, locale),
                    Analytics.total(Analytics.income(items)),
                    Analytics.total(Analytics.spending(items)),
                )
            }
        }

        // Running net stops at today: future days haven't happened.
        val lastIndex = when {
            !isCurrent -> series.lastIndex
            period.type == PeriodType.YEAR -> today.monthValue - 1
            else -> ChronoUnit.DAYS.between(period.start, today).toInt()
        }
        var running = 0.0
        val runningNet = series.take(lastIndex + 1).map { point ->
            running += point.net
            ChartPoint(point.label, running)
        }

        val recurring = recurring(entries, today)
        val lastMonth = YearMonth.from(if (period.end.isAfter(today)) today else period.end)
        val monthly = (5 downTo 0).map { back ->
            val month = lastMonth.minusMonths(back.toLong())
            val items = entries.filter { YearMonth.from(it.date) == month }
            CashPoint(
                month.month.getDisplayName(TextStyle.SHORT, locale),
                Analytics.total(Analytics.income(items)),
                Analytics.total(Analytics.spending(items)),
            )
        }
        // Only completed months count: the current month may simply be waiting for payday.
        val completed = if (lastMonth == YearMonth.from(today)) monthly.dropLast(1) else monthly
        val completedWithData = completed.filter { it.cashIn > 0 || it.cashOut > 0 }
        val anyIncome = completedWithData.any { it.cashIn > 0 }

        return CashFlowSummary(
            period = period,
            cashIn = Analytics.total(income),
            cashOut = Analytics.total(spending),
            previousIn = Analytics.total(Analytics.income(previousItems)),
            previousOut = Analytics.total(Analytics.spending(previousItems)),
            series = series,
            runningNet = runningNet,
            incomeBySource = Analytics.byCategory(income),
            recurringIncome = recurring.filter { it.type == EntryType.INCOME },
            recurringBills = recurring.filter { it.type == EntryType.EXPENSE },
            monthly = monthly,
            monthsOverspent = if (anyIncome) completedWithData.count { it.cashOut > it.cashIn } else 0,
            completedMonthsWithData = if (anyIncome) completedWithData.size else 0,
            outlook = if (isCurrent && period.type == PeriodType.MONTH) {
                outlook(Analytics.total(income), Analytics.total(spending), recurring, period, today)
            } else {
                null
            },
        )
    }

    private fun outlook(cashIn: Double, cashOut: Double, recurring: List<RecurringItem>, month: Period, today: LocalDate): MonthOutlook {
        val thisMonth = YearMonth.from(today)
        fun stillDue(item: RecurringItem) = !item.seenThisMonth && YearMonth.from(item.nextDate) == thisMonth
        val expectedIncome = recurring.filter { it.type == EntryType.INCOME && stillDue(it) }.sumOf { it.typicalAmount }
        val expectedBills = recurring.filter { it.type == EntryType.EXPENSE && stillDue(it) }.sumOf { it.typicalAmount }
        val billsPaid = recurring.filter { it.type == EntryType.EXPENSE && it.seenThisMonth }.sumOf { it.typicalAmount }

        val remainingDays = ChronoUnit.DAYS.between(today, month.end).toInt() + 1
        val elapsedDays = today.dayOfMonth
        // Day-to-day spending pace, leaving out one-off monthly bills so rent doesn't inflate it.
        val dailyPace = max(0.0, cashOut - billsPaid) / elapsedDays
        val projectedOut = cashOut + expectedBills + dailyPace * (remainingDays - 1)
        val projectedIn = cashIn + expectedIncome
        return MonthOutlook(
            remainingDays = remainingDays,
            expectedIncome = expectedIncome,
            expectedBills = expectedBills,
            projectedOut = projectedOut,
            projectedIn = projectedIn,
            safeToSpendPerDay = (cashIn + expectedIncome - cashOut - expectedBills) / remainingDays,
        )
    }

    /**
     * Finds monthly patterns in the last four months: entries from the same merchant (or the same
     * category when there's no note) that appear about once a month, in consecutive months (last
     * month, plus this month or the month before), on a similar day and for a similar amount.
     */
    fun recurring(entries: List<Expense>, today: LocalDate): List<RecurringItem> {
        val current = YearMonth.from(today)
        val windowStart = current.minusMonths(3).atDay(1)
        val window = entries.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(today) }

        return window.groupBy { e -> e.type to CategoryGuesser.merchantKey(e.note).ifEmpty { e.category.name } }
            .mapNotNull { (key, items) ->
                val byMonth = items.groupBy { YearMonth.from(it.date) }
                val months = byMonth.size
                if (months < 2) return@mapNotNull null
                if (items.size.toDouble() / months > 1.5) return@mapNotNull null // weekly coffee isn't a bill
                // Consecutive months: rules out every-other-month habits and things that stopped.
                val seen = byMonth.keys
                val previous = current.minusMonths(1)
                if (previous !in seen || (current !in seen && previous.minusMonths(1) !in seen)) return@mapNotNull null
                val median = median(items.map { it.amount })
                if (median <= 0 || items.any { abs(it.amount - median) > median * 0.35 }) return@mapNotNull null

                val day = median(items.map { it.date.dayOfMonth.toDouble() }).toInt()
                // Bills and salaries land on about the same day each month; random purchases don't.
                if (items.any { abs(it.date.dayOfMonth - day) > 6 }) return@mapNotNull null
                val seenThisMonth = byMonth.containsKey(current)
                val next = if (seenThisMonth) {
                    current.plusMonths(1).let { it.atDay(minOf(day, it.lengthOfMonth())) }
                } else {
                    val due = current.atDay(minOf(day, current.lengthOfMonth()))
                    if (due.isBefore(today)) today else due
                }
                val latest = items.maxBy { it.date }
                RecurringItem(
                    name = latest.note.ifBlank { latest.category.label },
                    category = items.groupingBy { it.category }.eachCount().maxBy { it.value }.key,
                    type = key.first,
                    typicalAmount = median,
                    dayOfMonth = day,
                    monthsSeen = months,
                    seenThisMonth = seenThisMonth,
                    nextDate = next,
                )
            }
            .sortedByDescending { it.typicalAmount }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
