package com.expensetracker.domain

import com.expensetracker.data.BankStatement
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.data.SAMPLE_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementStatusTest {
    private val statement = BankStatement(
        id = 7, fileName = "nov.pdf",
        periodStart = LocalDate.of(2026, 11, 1), periodEnd = LocalDate.of(2026, 11, 30),
        totalIn = 1000.0, totalOut = 150.0, transactionCount = 3, openingBalance = 200.0, closingBalance = 1050.0,
    )
    private fun e(amount: Double, category: Category, day: Int, statementId: Long? = 7, key: String? = "k$day") =
        Expense(amount = amount, category = category, date = LocalDate.of(2026, 11, day), statementId = statementId, importKey = key)

    @Test
    fun matchesWhenOnlyStatementEntriesAreInThePeriod() {
        val entries = listOf(e(1000.0, Category.SALARY, 25), e(100.0, Category.HOUSING, 1), e(50.0, Category.FOOD, 3))
        val status = StatementCheck.status(statement, entries)
        assertTrue(status.matches)
        assertEquals(850.0, status.appNet, 0.0)
    }

    @Test
    fun explainsExtraEntriesAndSampleData() {
        val entries = listOf(
            e(1000.0, Category.SALARY, 25), e(100.0, Category.HOUSING, 1), e(50.0, Category.FOOD, 3),
            e(12.0, Category.FOOD, 4, statementId = null, key = null), // typed in by hand
            e(30.0, Category.SHOPPING, 5, statementId = null, key = SAMPLE_PREFIX + "1"), // sample data
            e(99.0, Category.FOOD, 2, statementId = null).copy(date = LocalDate.of(2026, 10, 31)), // outside the period
        )
        val status = StatementCheck.status(statement, entries)
        assertFalse(status.matches)
        assertEquals(2, status.otherCount)
        assertEquals(1, status.sampleCount)
        assertEquals(42.0, status.otherOut, 0.0)
        assertEquals(192.0, status.appOut, 0.0)
    }

    @Test
    fun reportsStatementRowsThatWereNotImported() {
        val entries = listOf(e(1000.0, Category.SALARY, 25), e(100.0, Category.HOUSING, 1))
        val status = StatementCheck.status(statement, entries)
        assertFalse(status.matches)
        assertEquals(50.0, status.missingOut, 0.0)
        assertEquals(0.0, status.missingIn, 0.0)
    }

    @Test
    fun latestBalanceComesFromTheNewestStatement() {
        val older = statement.copy(id = 1, periodEnd = LocalDate.of(2026, 10, 31), closingBalance = 200.0)
        assertEquals(1050.0, StatementCheck.latestBalance(listOf(older, statement))!!.closingBalance!!, 0.0)
    }
}
