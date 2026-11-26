package com.expensetracker.ui

import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.ui.charts.labelStep
import com.expensetracker.ui.charts.niceAxisMax
import com.expensetracker.ui.settings.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChartScaleTest {
    @Test
    fun niceAxisMaxRoundsUp() {
        assertEquals(1.0, niceAxisMax(0.0), 0.0)
        assertEquals(50.0, niceAxisMax(43.0), 0.0)
        assertEquals(250.0, niceAxisMax(210.0), 0.0)
        assertEquals(1000.0, niceAxisMax(1000.0), 0.0)
        assertEquals(2_000_000.0, niceAxisMax(1_300_000.0), 0.0)
    }

    @Test
    fun labelStepThinsLongAxes() {
        assertEquals(1, labelStep(7))
        assertEquals(5, labelStep(31))
        assertEquals(2, labelStep(12))
    }

    @Test
    fun csvEscapesSpecialCharacters() {
        val csv = SettingsViewModel.buildCsv(
            listOf(Expense(amount = 4.5, category = Category.FOOD, note = "Coffee, \"large\"", date = LocalDate.of(2026, 11, 26))),
        )
        assertEquals("date,category,amount,note\n2026-11-26,Food & Dining,4.5,\"Coffee, \"\"large\"\"\"\n", csv)
    }
}
