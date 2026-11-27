package com.expensetracker.screenshots

import androidx.test.core.app.ApplicationProvider
import com.expensetracker.ExpenseApp
import com.expensetracker.data.OfflineRates
import com.expensetracker.data.RateSource
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Marks first-run setup as done before the activity launches (use with a lower rule order than the
 * compose rule), so tests start on the normal screens. Tests named in [except] see a fresh install.
 * Also swaps in a test source of exchange rates.
 */
class SkipFirstRunSetup(
    private val except: Set<String> = emptySet(),
    /** Exchange rates for the test; by default there's no network, so tests never download anything. */
    private val rates: () -> RateSource = { OfflineRates() },
) : TestWatcher() {
    override fun starting(description: Description) {
        val container = ApplicationProvider.getApplicationContext<ExpenseApp>().container
        container.rateSource = rates()
        if (description.methodName in except) return
        runBlocking { container.settingsRepository.setOnboarded() }
    }
}
