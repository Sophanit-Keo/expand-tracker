package com.expensetracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RatesUpdaterTest {
    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
    private var now = 1_000_000_000_000L
    private val prefs get() = runBlocking { settings.settings.first() }

    private fun updater(source: RateSource) = RatesUpdater(settings, { source }, { now })

    @Before
    fun reset(): Unit = runBlocking {
        // Settings outlive a test: start from USD, no rates, automatic updates on, never updated.
        settings.setCurrency(AppCurrency.EUR)
        settings.setCurrency(AppCurrency.USD)
        AppCurrency.entries.forEach { settings.removeRate(it) }
        settings.setAutoRates(true)
    }

    @Test
    fun downloadsRatesForTheCurrenciesYouUse() = runBlocking {
        settings.setRate(AppCurrency.KHR, 1 / 4000.0)
        settings.setRate(AppCurrency.THB, 1 / 35.0)
        assertTrue(updater(FixedRates()).update())
        val p = prefs
        assertEquals(1 / 4100.0, p.rates.getValue(AppCurrency.KHR), 1e-15)
        assertEquals(1 / 36.0, p.rates.getValue(AppCurrency.THB), 1e-15)
        assertEquals(setOf(AppCurrency.KHR, AppCurrency.THB), p.rates.keys) // nothing else added
        assertEquals(setOf(AppCurrency.KHR, AppCurrency.THB), p.onlineRates)
        assertEquals(now, p.ratesUpdatedAt)
        assertEquals("ExchangeRate-API", p.ratesProvider)
        // Typing a rate makes it yours again.
        settings.setRate(AppCurrency.KHR, 1 / 4050.0)
        assertEquals(setOf(AppCurrency.THB), prefs.onlineRates)
    }

    @Test
    fun addsARateForANewCurrency() = runBlocking {
        assertTrue(updater(FixedRates()).update(extra = setOf(AppCurrency.EUR)))
        assertEquals(1 / AppCurrency.EUR.perUsd, prefs.rates.getValue(AppCurrency.EUR), 1e-12)
    }

    @Test
    fun updatesOnlyWhenDue() = runBlocking {
        val source = FixedRates()
        val updater = updater(source)
        updater.updateIfDue()
        assertEquals(0, source.calls) // no rates saved: nothing to update
        settings.setRate(AppCurrency.KHR, 1 / 4000.0)
        updater.updateIfDue()
        assertEquals(1, source.calls)
        now += 2 * 60 * 60 * 1000L
        updater.updateIfDue()
        assertEquals(1, source.calls) // two hours later: still fresh
        now += 11 * 60 * 60 * 1000L
        updater.updateIfDue()
        assertEquals(2, source.calls)
        settings.setAutoRates(false)
        now += 24 * 60 * 60 * 1000L
        updater.updateIfDue()
        assertEquals(2, source.calls) // turned off
    }

    @Test
    fun offlineKeepsTheSavedRates() = runBlocking {
        settings.setRate(AppCurrency.KHR, 1 / 4000.0)
        val updater = updater(OfflineRates())
        assertFalse(updater.update())
        assertEquals(RateStatus.FAILED, updater.status.value)
        assertEquals(1 / 4000.0, prefs.rates.getValue(AppCurrency.KHR), 1e-15)
        assertEquals(0L, prefs.ratesUpdatedAt)
        // It recovers once online.
        val online = updater(FixedRates())
        assertTrue(online.update())
        assertEquals(RateStatus.IDLE, online.status.value)
    }

    @Test
    fun aMainCurrencyChangeDuringTheDownloadWins() = runBlocking {
        settings.setRate(AppCurrency.KHR, 1 / 4000.0)
        val slow = RateSource { base ->
            settings.setCurrency(AppCurrency.KHR) // the user switches while the rates download
            FixedRates().latest(base)
        }
        updater(slow).update()
        val p = prefs
        assertEquals(AppCurrency.KHR, p.currency)
        assertEquals(4000.0, p.rates.getValue(AppCurrency.USD), 1e-9) // rebased, not overwritten by USD rates
        assertEquals(0L, p.ratesUpdatedAt)
    }
}
