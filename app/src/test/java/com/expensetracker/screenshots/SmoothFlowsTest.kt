package com.expensetracker.screenshots

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.ExpenseApp
import com.expensetracker.MainActivity
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.ui.analytics.AnalyticsTab
import com.expensetracker.ui.analytics.AnalyticsTabs
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The 1.4 flow improvements: setup, quick fill, auto category, undo, duplicate, recurring, search. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
class SmoothFlowsTest {
    /** Setup is skipped except in the tests about setup itself. */
    @get:org.junit.Rule(order = 0)
    val skipSetup = SkipFirstRunSetup(except = setOf("firstRunSetupSavesCurrencyAndBudget", "existingUsersSkipSetup"))

    @get:org.junit.Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<ExpenseApp>().container
    private val today = LocalDate.now()

    private fun prepare(onboarded: Boolean, entries: List<Expense> = emptyList()) = runBlocking {
        AnalyticsTabs.selected.value = AnalyticsTab.SPENDING
        container.expenseRepository.clear()
        container.settingsRepository.setCurrency(AppCurrency.USD)
        container.settingsRepository.setMonthlyBudget(0.0)
        container.settingsRepository.setOnboarded(onboarded)
        if (entries.isNotEmpty()) container.expenseRepository.addAll(entries)
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test
    fun firstRunSetupSavesCurrencyAndBudget() {
        prepare(onboarded = false)
        compose.waitUntilAtLeastOneExists(hasText("Welcome to Expense Tracker"), timeoutMillis = 10_000)
        shot("0_welcome")
        compose.onNode(hasTestTag("currency_KHR")).performClick()
        compose.onNode(hasTestTag("onboarding_budget")).performTextInput("2000000")
        compose.onNode(hasTestTag("onboarding_scroll")).performScrollToNode(hasTestTag("onboarding_start"))
        compose.onNode(hasTestTag("onboarding_start")).performClick()
        compose.waitUntilDoesNotExist(hasTestTag("onboarding"), timeoutMillis = 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Start tracking your spending"), timeoutMillis = 10_000)

        val settings = runBlocking { container.settingsRepository.settings.first() }
        assertEquals(AppCurrency.KHR, settings.currency)
        assertEquals(2_000_000.0, settings.monthlyBudget, 0.0)
        assertTrue(settings.onboarded)
    }

    @Test
    fun existingUsersSkipSetup() {
        prepare(onboarded = false, entries = listOf(Expense(amount = 5.0, category = Category.FOOD, note = "Lunch", date = today)))
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
        assertTrue(runBlocking { container.settingsRepository.settings.first().onboarded })
        compose.waitUntilDoesNotExist(hasTestTag("onboarding"), timeoutMillis = 10_000)
    }

    @Test
    fun quickFillSaveAndUndo() {
        prepare(
            onboarded = true,
            entries = listOf(
                Expense(amount = 2.5, category = Category.FOOD, note = "Coffee", date = today.minusDays(2)),
                Expense(amount = 2.5, category = Category.FOOD, note = "Coffee", date = today.minusDays(1)),
            ),
        )
        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("quick_fill"), timeoutMillis = 10_000)
        shot("4c_quick_fill")
        compose.onAllNodes(hasTestTag("quick_fill")).onFirst().performClick()
        compose.onNodeWithText("Save expense").performClick()

        // "Saved · Undo" appears on Home; Undo removes the new entry again.
        compose.waitUntilAtLeastOneExists(hasText("Saved “Coffee”", substring = true), timeoutMillis = 10_000)
        assertEquals(3, runBlocking { container.expenseRepository.getAll() }.size)
        shot("1c_saved_snackbar")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { runBlocking { container.expenseRepository.getAll() }.size == 2 }
    }

    @Test
    fun categoryIsPickedFromTheNote() {
        prepare(onboarded = true)
        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Save expense") and isEnabled(), timeoutMillis = 10_000)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("3.2")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Grab to office")
        compose.waitUntilAtLeastOneExists(hasText("Picked from your note"), timeoutMillis = 5_000)
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(10_000) { runBlocking { container.expenseRepository.getAll() }.size == 1 }
        assertEquals(Category.TRANSPORT, runBlocking { container.expenseRepository.getAll() }.single().category)
    }

    @Test
    fun duplicateOpensAPrefilledNewEntry() {
        prepare(onboarded = true, entries = listOf(Expense(amount = 15.99, category = Category.ENTERTAINMENT, note = "Netflix", date = today)))
        compose.onNode(hasTestTag("nav_history")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Netflix"), timeoutMillis = 10_000)
        compose.onNodeWithText("Netflix").performClick()
        // Duplicate appears once the entry has loaded.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Duplicate as a new entry"), timeoutMillis = 10_000)
        compose.onNode(hasContentDescription("Duplicate as a new entry")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New expense"), timeoutMillis = 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Save expense") and isEnabled(), timeoutMillis = 10_000)
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(10_000) { runBlocking { container.expenseRepository.getAll() }.size == 2 }
        assertTrue(runBlocking { container.expenseRepository.getAll() }.all { it.note == "Netflix" && it.amount == 15.99 })
    }

    @Test
    fun recurringItemLogsInOneTap() {
        val salaries = (1L..3L).map { back ->
            val month = today.minusMonths(back)
            Expense(amount = 1450.0, category = Category.SALARY, note = "Salary", date = month.withDayOfMonth(minOf(25, month.lengthOfMonth())))
        }
        prepare(onboarded = true, entries = salaries)
        AnalyticsTabs.openCashFlow()
        compose.onNode(hasTestTag("nav_analytics")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Smart insights"), timeoutMillis = 10_000)
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("recurring_row"))
        compose.onAllNodes(hasTestTag("recurring_row")).onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("New income"), timeoutMillis = 10_000)
        compose.waitUntilAtLeastOneExists(hasText("1450"), timeoutMillis = 10_000) // pre-filled amount
        compose.waitUntilAtLeastOneExists(hasText("Save income") and isEnabled(), timeoutMillis = 10_000)
        compose.onNodeWithText("Save income").performClick()
        compose.waitUntil(10_000) { runBlocking { container.expenseRepository.getAll() }.size == 4 }
        val logged = runBlocking { container.expenseRepository.getAll() }.first { it.date == today }
        assertEquals(1450.0, logged.amount, 0.0)
        assertEquals(Category.SALARY, logged.category)
    }

    @Test
    fun searchLooksThroughAllMonths() {
        prepare(
            onboarded = true,
            entries = listOf(
                Expense(amount = 40.0, category = Category.HEALTH, note = "Dentist", date = today.minusMonths(3)),
                Expense(amount = 5.0, category = Category.FOOD, note = "Lunch", date = today),
            ),
        )
        compose.onNode(hasTestTag("nav_history")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Search notes or categories"), timeoutMillis = 10_000)
        compose.onNode(hasSetTextAction()).performTextInput("dentist")
        compose.waitUntilAtLeastOneExists(hasText("Results from all months"), timeoutMillis = 5_000)
        compose.waitUntilAtLeastOneExists(hasText("Dentist"), timeoutMillis = 5_000)
    }
}
