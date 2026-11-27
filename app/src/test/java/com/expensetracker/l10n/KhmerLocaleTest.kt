package com.expensetracker.l10n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "km")
class KhmerLocaleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun khmerResourcesAreUsed() {
        assertEquals("ទំព័រដើម", context.getString(R.string.nav_home))
        assertEquals("នៅសល់ 3 ថ្ងៃ", context.resources.getQuantityString(R.plurals.days_left, 3, 3))
        // Khmer has one plural form, also for 1.
        assertEquals("នៅសល់ 1 ថ្ងៃ", context.resources.getQuantityString(R.plurals.days_left, 1, 1))
    }

    @Test
    fun datesUseKhmerMonthNames() {
        val locale = context.resources.configuration.locales[0]
        assertEquals("km", locale.language)
        val date = LocalDate.of(2026, 11, 23)
        val monthYear = DateTimeFormatter.ofPattern(context.getString(R.string.pattern_month_year), locale).format(date)
        assertTrue(monthYear, monthYear.startsWith("វិច្ឆិកា"))
        val short = DateTimeFormatter.ofPattern(context.getString(R.string.pattern_short_day), locale).format(date)
        assertTrue(short, short.startsWith("23 "))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EnglishLocaleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun englishDatesKeepTheirFormat() {
        fun format(id: Int, date: LocalDate) = DateTimeFormatter.ofPattern(context.getString(id), Locale.ENGLISH).format(date)
        assertEquals("Mon, Nov 23", format(R.string.pattern_day, LocalDate.of(2026, 11, 23)))
        assertEquals("Fri, Dec 26, 2025", format(R.string.pattern_day_year, LocalDate.of(2025, 12, 26)))
        assertEquals("Today", context.getString(R.string.today))
        assertEquals("1 day left", context.resources.getQuantityString(R.plurals.days_left, 1, 1))
    }
}
