package com.expensetracker.screenshots

import androidx.test.core.app.ApplicationProvider
import com.expensetracker.ExpenseApp
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Marks first-run setup as done before the activity launches (use with a lower rule order than the
 * compose rule), so tests start on the normal screens. Tests named in [except] see a fresh install.
 */
class SkipFirstRunSetup(private val except: Set<String> = emptySet()) : TestWatcher() {
    override fun starting(description: Description) {
        if (description.methodName in except) return
        val container = ApplicationProvider.getApplicationContext<ExpenseApp>().container
        runBlocking { container.settingsRepository.setOnboarded() }
    }
}
