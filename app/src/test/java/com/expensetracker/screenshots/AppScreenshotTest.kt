package com.expensetracker.screenshots

import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.ExpenseApp
import com.expensetracker.MainActivity
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.expensetracker.data.FixedRates
import com.expensetracker.data.SampleData
import com.expensetracker.ui.analytics.AnalyticsTab
import com.expensetracker.ui.analytics.AnalyticsTabs
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Drives the real app (Room + DataStore + navigation) on the JVM and saves screenshots to
 * `docs/screenshots`. Run with: ./gradlew recordRoborazziDebug
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
abstract class AppScreenshotTest(
    private val suffix: String,
    private val directory: String = "../docs/screenshots",
    /** When set, only these screens are saved (e.g. the Play Store set). */
    private val only: Set<String>? = null,
) {
    /** Runs before the activity starts, so first-run setup never covers the screen. */
    @get:Rule(order = 0)
    val skipSetup = SkipFirstRunSetup(rates = { FixedRates() })

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() {
        AnalyticsTabs.selected.value = AnalyticsTab.SPENDING
        val container = ApplicationProvider.getApplicationContext<ExpenseApp>().container
        runBlocking {
            container.expenseRepository.clear()
            container.expenseRepository.addAll(SampleData.generate(AppCurrency.USD))
            container.settingsRepository.setCurrency(AppCurrency.USD)
            container.settingsRepository.setMonthlyBudget(1_500.0)
            // A couple of things paid in riel, converted at the saved rate.
            AppCurrency.entries.forEach { container.settingsRepository.removeRate(it) }
            container.settingsRepository.setRate(AppCurrency.KHR, 1 / 4100.0)
            container.settingsRepository.setRate(AppCurrency.THB, 1 / 36.0)
            container.settingsRepository.setAutoRates(true)
            container.ratesUpdater.update() // today's rates "from the internet"
            val today = LocalDate.now()
            container.expenseRepository.addAll(
                listOf(
                    Expense(amount = 12_000 / 4100.0, category = Category.FOOD, note = "Num banh chok", date = today, originalAmount = 12_000.0, originalCurrency = AppCurrency.KHR),
                    Expense(amount = 8_000 / 4100.0, category = Category.TRANSPORT, note = "Tuk-tuk", date = today.minusDays(1), originalAmount = 8_000.0, originalCurrency = AppCurrency.KHR),
                ),
            )
        }
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        if (only != null && name !in only) return
        compose.onRoot().captureRoboImage("$directory/$name$suffix.png")
    }

    /** Text in the language the test runs in, so the same walk-through works in English and Khmer. */
    private fun t(@StringRes id: Int): String = compose.activity.getString(id)

    private fun open(route: String, @StringRes waitFor: Int) {
        compose.onNode(hasTestTag("nav_$route")).performClick()
        compose.waitUntilAtLeastOneExists(hasText(t(waitFor)), timeoutMillis = 10_000)
    }

    private fun scrollTo(@StringRes id: Int) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(t(id)))
    }

    @Test
    fun allScreens() {
        compose.waitUntilAtLeastOneExists(hasText(t(R.string.spent_in).substringBefore("%").trim(), substring = true), timeoutMillis = 10_000)
        shoot("1_home")
        scrollTo(R.string.recent_transactions)
        shoot("1b_home_scrolled")

        open("analytics", R.string.by_category)
        shoot("2_analytics")
        scrollTo(R.string.spending_over_time)
        shoot("2b_analytics_charts")
        scrollTo(R.string.insights)
        shoot("2c_analytics_insights")

        compose.onNode(hasTestTag("tab_cash_flow")).performClick()
        compose.waitUntilAtLeastOneExists(hasText(t(R.string.smart_insights)), timeoutMillis = 10_000)
        shoot("9_cash_flow")
        scrollTo(R.string.cash_in_vs_out)
        shoot("9b_cash_flow_charts")
        scrollTo(R.string.money_comes_from)
        shoot("9c_cash_flow_recurring")
        scrollTo(R.string.last_6_months)
        shoot("9d_cash_flow_months")

        open("history", R.string.search_hint)
        shoot("3_history")

        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText(t(R.string.edit_title_new_expense)), timeoutMillis = 10_000)
        shoot("4_add_expense")
        compose.onNode(hasTestTag("type_income")).performClick()
        compose.waitUntilAtLeastOneExists(hasText(t(R.string.edit_title_new_income)), timeoutMillis = 10_000)
        shoot("4b_add_income")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilAtLeastOneExists(hasTestTag("nav_settings"), timeoutMillis = 10_000)

        // An expense paid in riel: pick the currency, the saved rate is filled in.
        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText(t(R.string.edit_title_new_expense)), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("entry_currency")).performClick()
        compose.onNode(hasTestTag("pick_currency_KHR")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("rate_input"), timeoutMillis = 10_000)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("12000")
        compose.onNode(hasTestTag("type_expense")).performClick() // closes the keyboard focus ring
        shoot("4c_add_expense_currency")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilAtLeastOneExists(hasTestTag("nav_settings"), timeoutMillis = 10_000)

        open("settings", R.string.monthly_budget)
        shoot("5_settings")
        compose.onNode(hasTestTag("settings_rates")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("rate_KHR"), timeoutMillis = 10_000)
        shoot("5b_exchange_rates")
    }
}

class LightScreenshotTest : AppScreenshotTest("")

/** Google Play phone screenshots: 1233×2193 (16:9, under Play's 2:1 limit). */
@Config(qualifiers = "w411dp-h731dp-xxhdpi")
class PlayStoreScreenshotTest : AppScreenshotTest(
    suffix = "",
    directory = "../docs/play-store/phone",
    only = setOf("1_home", "9_cash_flow", "9b_cash_flow_charts", "2_analytics", "2b_analytics_charts", "3_history", "4_add_expense", "4c_add_expense_currency"),
)

@Config(qualifiers = "+night")
class DarkScreenshotTest : AppScreenshotTest("_dark")

/** The same screens in Khmer (phone language set to Khmer). */
@Config(qualifiers = "km-w400dp-h880dp-xxhdpi")
class KhmerScreenshotTest : AppScreenshotTest("_km")
