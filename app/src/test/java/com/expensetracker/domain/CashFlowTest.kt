package com.expensetracker.domain

import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class CashFlowTest {
    // Thursday, 26 Nov 2026
    private val today = LocalDate.of(2026, 11, 26)

    private fun e(amount: Double, date: LocalDate, category: Category, note: String = "") =
        Expense(amount = amount, category = category, note = note, date = date)

    /** Salary on the 28th, rent on the 1st, coffee most days, for Aug–Nov. */
    private fun history(): List<Expense> = buildList {
        for (month in 8..11) {
            add(e(320.0, LocalDate.of(2026, month, 1), Category.HOUSING, "Rent"))
            add(e(20.0, LocalDate.of(2026, month, 8), Category.BILLS, "Internet"))
            if (month < 11) add(e(1500.0, LocalDate.of(2026, month, 28), Category.SALARY, "Salary"))
            for (day in 2..20 step 2) add(e(4.0, LocalDate.of(2026, month, day), Category.FOOD, "Coffee"))
        }
        add(e(900.0, LocalDate.of(2026, 9, 15), Category.TRAVEL, "Holiday")) // September overspends
        add(e(800.0, LocalDate.of(2026, 9, 16), Category.SHOPPING, "Laptop"))
    }

    @Test
    fun totalsNetAndSavingsRate() {
        val entries = listOf(
            e(1000.0, LocalDate.of(2026, 11, 3), Category.SALARY),
            e(250.0, LocalDate.of(2026, 11, 4), Category.FOOD),
            e(50.0, LocalDate.of(2026, 11, 5), Category.REFUND),
            e(100.0, LocalDate.of(2026, 10, 3), Category.FOOD),
            e(400.0, LocalDate.of(2026, 10, 3), Category.SALARY),
        )
        val s = CashFlow.summarize(entries, Period.of(PeriodType.MONTH, today), today, Locale.US)
        assertEquals(1050.0, s.cashIn, 0.0)
        assertEquals(250.0, s.cashOut, 0.0)
        assertEquals(800.0, s.net, 0.0)
        assertEquals(800.0 / 1050.0, s.savingsRate!!, 1e-9)
        assertEquals(0.75, s.previousSavingsRate!!, 1e-9)
        assertEquals(listOf(Category.SALARY, Category.REFUND), s.incomeBySource.map { it.category })
        assertEquals(30, s.series.size)
        assertEquals(1000.0, s.series[2].cashIn, 0.0)
        // Running net stops at today (26th) and ends at the month's net.
        assertEquals(26, s.runningNet.size)
        assertEquals(800.0, s.runningNet.last().value, 1e-9)
    }

    @Test
    fun findsMonthlyIncomeAndBillsButNotDailyHabits() {
        val recurring = CashFlow.recurring(history(), today)
        val names = recurring.map { it.name }
        assertTrue("Salary" in names)
        assertTrue("Rent" in names)
        assertTrue("Internet" in names)
        assertTrue("Coffee" !in names) // ten times a month
        assertTrue("Holiday" !in names) // once
        val salary = recurring.first { it.name == "Salary" }
        assertEquals(EntryType.INCOME, salary.type)
        assertEquals(28, salary.dayOfMonth)
        assertEquals(false, salary.seenThisMonth)
        assertEquals(LocalDate.of(2026, 11, 28), salary.nextDate)
        val rent = recurring.first { it.name == "Rent" }
        assertEquals(LocalDate.of(2026, 12, 1), rent.nextDate)
    }

    @Test
    fun ignoresEveryOtherMonthAndRandomDays() {
        val entries = history() + listOf(
            // Every other month: Aug and Oct only.
            e(100.0, LocalDate.of(2026, 8, 20), Category.TRAVEL, "Weekend trip"),
            e(110.0, LocalDate.of(2026, 10, 20), Category.TRAVEL, "Weekend trip"),
            // Monthly but on scattered days: a coincidence, not a bill.
            e(12.0, LocalDate.of(2026, 9, 3), Category.OTHER, "Haircut"),
            e(12.0, LocalDate.of(2026, 10, 24), Category.OTHER, "Haircut"),
        )
        val names = CashFlow.recurring(entries, today).map { it.name }
        assertTrue("Weekend trip" !in names)
        assertTrue("Haircut" !in names)
        assertTrue("Salary" in names)
    }

    @Test
    fun outlookCountsSalaryStillToCome() {
        val s = CashFlow.summarize(history(), Period.of(PeriodType.MONTH, today), today, Locale.US)
        val outlook = assertNotNull(s.outlook).let { s.outlook!! }
        // November so far: rent 320 + internet 20 + coffee 10×4 = 380 out, nothing in yet.
        assertEquals(380.0, s.cashOut, 1e-9)
        assertEquals(0.0, s.cashIn, 0.0)
        assertEquals(1500.0, outlook.expectedIncome, 0.0)
        assertEquals(0.0, outlook.expectedBills, 0.0)
        assertEquals(5, outlook.remainingDays) // 26th..30th
        assertEquals((1500.0 - 380.0) / 5, outlook.safeToSpendPerDay, 1e-9)
        // Pace excludes the rent and internet already paid: 40 / 26 days.
        assertEquals(380.0 + 40.0 / 26 * 4, outlook.projectedOut, 1e-9)
    }

    @Test
    fun overspentMonthsIgnoreTheCurrentMonth() {
        val s = CashFlow.summarize(history(), Period.of(PeriodType.MONTH, today), today, Locale.US)
        assertEquals(listOf("Jun", "Jul", "Aug", "Sep", "Oct", "Nov"), s.monthly.map { it.label })
        // Aug and Oct saved money, Sep (holiday + laptop) didn't; Nov is still in progress.
        assertEquals(1, s.monthsOverspent)
        assertEquals(3, s.completedMonthsWithData)
        assertEquals(1500.0, s.averageMonthlyIncome!!, 0.0)
    }

    @Test
    fun pastPeriodsHaveNoOutlook() {
        val s = CashFlow.summarize(history(), Period.of(PeriodType.MONTH, today, -1), today, Locale.US)
        assertNull(s.outlook)
        assertEquals(31, s.runningNet.size)
    }

    @Test
    fun spendingAnalyticsIgnoreIncome() {
        val entries = listOf(e(1000.0, today, Category.SALARY), e(30.0, today, Category.FOOD))
        assertEquals(listOf(30.0), Analytics.spending(entries).map { it.amount })
        assertEquals(EntryType.INCOME, entries[0].type)
    }
}
