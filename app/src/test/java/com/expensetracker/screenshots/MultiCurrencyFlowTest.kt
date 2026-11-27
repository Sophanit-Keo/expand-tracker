package com.expensetracker.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.ExpenseApp
import com.expensetracker.MainActivity
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.data.FixedRates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Expenses in another currency, with an exchange rate, through the real UI. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h880dp")
class MultiCurrencyFlowTest {
    @get:Rule(order = 0)
    val skipSetup = SkipFirstRunSetup()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<ExpenseApp>().container
    private val settings get() = runBlocking { container.settingsRepository.settings.first() }

    @Before
    fun reset(): Unit = runBlocking {
        container.expenseRepository.clear()
        container.settingsRepository.setCurrency(AppCurrency.USD)
        AppCurrency.entries.forEach { container.settingsRepository.removeRate(it) }
        container.settingsRepository.setAutoRates(true)
    }

    private fun openNewExpense() {
        compose.waitUntilAtLeastOneExists(hasTestTag("add_expense"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Save expense") and isEnabled(), timeoutMillis = 10_000)
    }

    /** Waits for work on other threads (DataStore, Room) while letting the main thread run. */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            Thread.sleep(20)
        }
        assertEquals(true, condition())
    }

    private fun openHistory() {
        compose.waitUntilAtLeastOneExists(hasTestTag("nav_history"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("nav_history")).performClick()
    }

    private fun pickCurrency(currency: AppCurrency) {
        compose.onNode(hasTestTag("entry_currency")).performClick()
        compose.onNode(hasTestTag("pick_currency_${currency.name}")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("rate_input"), timeoutMillis = 5_000)
    }

    @Test
    fun expenseInRielIsConvertedAndTheRateRemembered() {
        openHistory()
        openNewExpense()
        pickCurrency(AppCurrency.KHR)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("12000")
        compose.onNode(hasTestTag("rate_input")).performTextInput("4000")
        compose.waitUntilAtLeastOneExists(hasText("Counts as $3.00"), timeoutMillis = 5_000)
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntilDoesNotExist(hasText("Save expense"), timeoutMillis = 10_000)

        val saved = runBlocking { container.expenseRepository.getAll() }.single()
        assertEquals(3.0, saved.amount, 1e-9)
        assertEquals(12_000.0 to AppCurrency.KHR, saved.foreign)
        // The rate is suggested next time.
        assertEquals(1 / 4000.0, settings.rates.getValue(AppCurrency.KHR), 1e-15)
        // Lists show the main-currency amount and what was paid.
        compose.waitUntilAtLeastOneExists(hasText("-$3.00"), timeoutMillis = 10_000)
        compose.onNodeWithText("-12,000៛").assertExists()

        openNewExpense()
        pickCurrency(AppCurrency.KHR)
        compose.onNode(hasTestTag("rate_input") and hasText("4000")).assertExists()
    }

    @Test
    fun aForeignAmountNeedsARate() {
        openNewExpense()
        pickCurrency(AppCurrency.THB)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("120")
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Enter the exchange rate"), timeoutMillis = 5_000)
        assertEquals(0, runBlocking { container.expenseRepository.getAll() }.size)
    }

    @Test
    fun editingKeepsTheEntrysCurrencyAndRate() {
        runBlocking {
            container.expenseRepository.add(
                Expense(amount = 3.0, category = Category.FOOD, note = "Noodles", date = LocalDate.now(), originalAmount = 12_000.0, originalCurrency = AppCurrency.KHR),
            )
        }
        openHistory()
        compose.waitUntilAtLeastOneExists(hasText("Noodles"), timeoutMillis = 10_000)
        compose.onNodeWithText("Noodles").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Save changes") and isEnabled(), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("entry_currency") and hasText("KHR", substring = true)).assertExists()
        compose.onNode(hasTestTag("rate_input") and hasText("4000")).assertExists()
        // A better rate changes this entry only.
        compose.onNode(hasTestTag("rate_input")).performTextClearance()
        compose.onNode(hasTestTag("rate_input")).performTextInput("4100")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntilDoesNotExist(hasText("Save changes"), timeoutMillis = 10_000)
        val saved = runBlocking { container.expenseRepository.getAll() }.single()
        assertEquals(12_000 / 4100.0, saved.amount, 1e-9)
        assertEquals(12_000.0 to AppCurrency.KHR, saved.foreign)
    }

    @Test
    fun ratesCanBeAddedInSettings() {
        compose.waitUntilAtLeastOneExists(hasTestTag("nav_settings"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("nav_settings")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("settings_rates"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("settings_rates")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("add_rate"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("add_rate")).performClick()
        compose.onNode(hasTestTag("add_rate_THB")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("rate_THB"), timeoutMillis = 10_000)
        // Main USD: written "1 USD = 36 THB".
        compose.onNodeWithText("1 USD =").assertExists()
        compose.onNode(hasTestTag("quote_input")).performTextInput("36")
        waitFor { AppCurrency.THB in settings.rates }
        assertEquals(1 / 36.0, settings.rates.getValue(AppCurrency.THB), 1e-12)
    }

    @Test
    fun todaysRateIsFilledInFromTheInternet() {
        container.rateSource = FixedRates()
        openHistory()
        openNewExpense()
        pickCurrency(AppCurrency.THB)
        // Main USD: "1 USD = 36 THB", downloaded.
        compose.waitUntilAtLeastOneExists(hasTestTag("rate_input") and hasText("36"), timeoutMillis = 10_000)
        compose.onNodeWithText("Today's rate from the internet. You can change it for this entry.").assertExists()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("90")
        compose.waitUntilAtLeastOneExists(hasText("Counts as $2.50"), timeoutMillis = 5_000)
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntilDoesNotExist(hasText("Save expense"), timeoutMillis = 10_000)
        val saved = runBlocking { container.expenseRepository.getAll() }.single()
        assertEquals(2.5, saved.amount, 1e-12)
        // Kept as the downloaded rate, so the next update refreshes it.
        assertEquals(setOf(AppCurrency.THB), settings.onlineRates)
    }

    @Test
    fun updateNowRefreshesSavedRates() {
        runBlocking { container.settingsRepository.setRate(AppCurrency.KHR, 1 / 4000.0) }
        container.rateSource = FixedRates()
        compose.waitUntilAtLeastOneExists(hasTestTag("nav_settings"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("nav_settings")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("settings_rates"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("settings_rates")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("update_rates"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("update_rates")).performClick()
        waitFor { AppCurrency.KHR in settings.onlineRates }
        assertEquals(1 / 4100.0, settings.rates.getValue(AppCurrency.KHR), 1e-15)
        compose.waitUntilAtLeastOneExists(hasTestTag("quote_input") and hasText("4100"), timeoutMillis = 5_000)
        compose.waitUntilAtLeastOneExists(hasText("Rates by ExchangeRate-API", substring = true), timeoutMillis = 5_000)
    }

    @Test
    fun ratesFollowTheMainCurrency() = runBlocking {
        container.settingsRepository.setRate(AppCurrency.KHR, 1 / 4000.0)
        container.settingsRepository.setRate(AppCurrency.THB, 1 / 36.0)
        container.settingsRepository.setCurrency(AppCurrency.KHR)
        val rates = settings.rates
        assertEquals(4000.0, rates.getValue(AppCurrency.USD), 1e-9)
        assertEquals(4000.0 / 36, rates.getValue(AppCurrency.THB), 1e-9)
        assertEquals(setOf(AppCurrency.USD, AppCurrency.THB), rates.keys)
    }
}
