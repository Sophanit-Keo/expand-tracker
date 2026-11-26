package com.expensetracker.domain

import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class AnalyticsTest {
    // Thursday, 26 Nov 2026
    private val today = LocalDate.of(2026, 11, 26)

    private fun expense(amount: Double, date: LocalDate, category: Category = Category.FOOD) =
        Expense(amount = amount, category = category, date = date)

    @Test
    fun weekPeriodStartsOnMonday() {
        val week = Period.of(PeriodType.WEEK, today)
        assertEquals(LocalDate.of(2026, 11, 23), week.start)
        assertEquals(LocalDate.of(2026, 11, 29), week.end)
        assertEquals(DayOfWeek.MONDAY, week.start.dayOfWeek)
    }

    @Test
    fun periodOffsetsMoveBackwards() {
        assertEquals(LocalDate.of(2026, 10, 1), Period.of(PeriodType.MONTH, today, -1).start)
        assertEquals(LocalDate.of(2026, 10, 31), Period.of(PeriodType.MONTH, today, -1).end)
        assertEquals(LocalDate.of(2025, 1, 1), Period.of(PeriodType.YEAR, today, -1).start)
        assertEquals(LocalDate.of(2026, 11, 16), Period.of(PeriodType.WEEK, today, -1).start)
    }

    @Test
    fun previousMonthHandlesDifferentLengths() {
        val march = Period.of(PeriodType.MONTH, LocalDate.of(2026, 3, 15))
        val feb = march.previous()
        assertEquals(LocalDate.of(2026, 2, 1), feb.start)
        assertEquals(LocalDate.of(2026, 2, 28), feb.end)
    }

    @Test
    fun elapsedDaysStopsAtToday() {
        val month = Period.of(PeriodType.MONTH, today)
        assertEquals(26, month.elapsedDays(today))
        assertEquals(31, Period.of(PeriodType.MONTH, today, -1).elapsedDays(today))
        assertEquals(0, Period.of(PeriodType.MONTH, today, 1).elapsedDays(today))
    }

    @Test
    fun byCategorySortsAndComputesShares() {
        val items = listOf(
            expense(30.0, today, Category.FOOD),
            expense(10.0, today, Category.TRANSPORT),
            expense(60.0, today, Category.SHOPPING),
        )
        val result = Analytics.byCategory(items)
        assertEquals(listOf(Category.SHOPPING, Category.FOOD, Category.TRANSPORT), result.map { it.category })
        assertEquals(0.6f, result[0].share, 0.0001f)
        assertEquals(1.0f, result.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun byCategoryIsEmptyWithoutSpending() {
        assertTrue(Analytics.byCategory(emptyList()).isEmpty())
    }

    @Test
    fun dailySeriesFillsMissingDaysWithZero() {
        val items = listOf(expense(5.0, today), expense(7.0, today), expense(3.0, today.minusDays(2)))
        val series = Analytics.dailySeries(items, today.minusDays(3), today) { it.dayOfMonth.toString() }
        assertEquals(listOf(0.0, 3.0, 0.0, 12.0), series.map { it.value })
        assertEquals("26", series.last().label)
    }

    @Test
    fun monthlySeriesIsOldestFirst() {
        val items = listOf(
            expense(100.0, LocalDate.of(2026, 11, 2)),
            expense(50.0, LocalDate.of(2026, 9, 10)),
            expense(999.0, LocalDate.of(2026, 1, 10)), // outside the window
        )
        val series = Analytics.monthlySeries(items, YearMonth.of(2026, 11), 3, Locale.US)
        assertEquals(listOf("Sep", "Oct", "Nov"), series.map { it.label })
        assertEquals(listOf(50.0, 0.0, 100.0), series.map { it.value })
    }

    @Test
    fun summarizeComputesTotalsAverageAndComparison() {
        val items = listOf(
            expense(40.0, LocalDate.of(2026, 11, 3), Category.FOOD),
            expense(80.0, LocalDate.of(2026, 11, 21), Category.TRAVEL), // a Saturday
            expense(10.0, LocalDate.of(2026, 11, 26), Category.FOOD),
            expense(100.0, LocalDate.of(2026, 10, 5), Category.BILLS),
        )
        val summary = Analytics.summarize(items, Period.of(PeriodType.MONTH, today), today, Locale.US)
        assertEquals(130.0, summary.total, 0.0001)
        assertEquals(100.0, summary.previousTotal, 0.0001)
        assertEquals(30.0, summary.changePercent!!, 0.0001)
        assertEquals(3, summary.count)
        assertEquals(130.0 / 26, summary.dailyAverage, 0.0001)
        assertEquals(30, summary.series.size)
        assertEquals(80.0, summary.largest!!.amount, 0.0001)
        assertEquals(DayOfWeek.SATURDAY, summary.topWeekday)
        assertEquals(Category.TRAVEL, summary.byCategory.first().category)
    }

    @Test
    fun summarizeYearUsesTwelveMonthlyPoints() {
        val items = listOf(expense(20.0, LocalDate.of(2026, 2, 1)), expense(5.0, LocalDate.of(2026, 11, 1)))
        val summary = Analytics.summarize(items, Period.of(PeriodType.YEAR, today), today, Locale.US)
        assertEquals(12, summary.series.size)
        assertEquals("Jan", summary.series.first().label)
        assertEquals(20.0, summary.series[1].value, 0.0001)
        assertNull(summary.changePercent)
    }
}
