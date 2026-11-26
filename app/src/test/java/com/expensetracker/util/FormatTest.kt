package com.expensetracker.util

import com.expensetracker.data.AppCurrency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test
    fun formatsMoneyWithSymbolPosition() {
        assertEquals("$1,234.50", formatMoney(1234.5, AppCurrency.USD))
        assertEquals("12,000៛", formatMoney(12000.0, AppCurrency.KHR))
        assertEquals("-€3.00", formatMoney(-3.0, AppCurrency.EUR))
    }

    @Test
    fun formatsCompactValues() {
        assertEquals("$950", formatCompact(950.0, AppCurrency.USD))
        assertEquals("$1.2K", formatCompact(1234.0, AppCurrency.USD))
        assertEquals("$250K", formatCompact(250_000.0, AppCurrency.USD))
        assertEquals("4.5M៛", formatCompact(4_500_000.0, AppCurrency.KHR))
    }

    @Test
    fun sanitizesAmountInput() {
        assertEquals("12.34", sanitizeAmountInput("12.345", 2))
        assertEquals("12.3", sanitizeAmountInput("12,3", 2))
        assertEquals("1.23", sanitizeAmountInput("1.2.3", 2))
        assertEquals("5000", sanitizeAmountInput("5,000", 0))
        assertEquals("42", sanitizeAmountInput("4a2", 2))
    }

    @Test
    fun amountToInputDropsTrailingZeros() {
        assertEquals("12.5", amountToInput(12.5))
        assertEquals("3000", amountToInput(3000.0))
    }

    @Test
    fun friendlyDates() {
        val today = LocalDate.of(2026, 11, 26)
        assertEquals("Today", friendlyDate(today, today))
        assertEquals("Yesterday", friendlyDate(today.minusDays(1), today))
        assertEquals("Mon, Nov 23", friendlyDate(LocalDate.of(2026, 11, 23), today))
        assertEquals("Fri, Dec 26, 2025", friendlyDate(LocalDate.of(2025, 12, 26), today))
    }
}
