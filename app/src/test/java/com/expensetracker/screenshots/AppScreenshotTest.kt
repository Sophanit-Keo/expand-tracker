package com.expensetracker.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.ExpenseApp
import com.expensetracker.MainActivity
import com.expensetracker.data.AppCurrency
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
    val skipSetup = SkipFirstRunSetup()

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
        }
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        if (only != null && name !in only) return
        compose.onRoot().captureRoboImage("$directory/$name$suffix.png")
    }

    private fun open(route: String, waitForText: String) {
        compose.onNode(hasTestTag("nav_$route")).performClick()
        compose.waitUntilAtLeastOneExists(hasText(waitForText), timeoutMillis = 10_000)
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun allScreens() {
        compose.waitUntilAtLeastOneExists(hasText("Spent in", substring = true), timeoutMillis = 10_000)
        shoot("1_home")
        scrollTo("Recent transactions")
        shoot("1b_home_scrolled")

        open("analytics", "By category")
        shoot("2_analytics")
        scrollTo("Spending over time")
        shoot("2b_analytics_charts")
        scrollTo("Insights")
        shoot("2c_analytics_insights")

        compose.onNode(hasTestTag("tab_cash_flow")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Smart insights"), timeoutMillis = 10_000)
        shoot("9_cash_flow")
        scrollTo("Cash in vs cash out")
        shoot("9b_cash_flow_charts")
        scrollTo("Where your money comes from")
        shoot("9c_cash_flow_recurring")
        scrollTo("Last 6 months")
        shoot("9d_cash_flow_months")

        open("history", "Search notes or categories")
        shoot("3_history")

        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New expense"), timeoutMillis = 10_000)
        shoot("4_add_expense")
        compose.onNode(hasTestTag("type_income")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New income"), timeoutMillis = 10_000)
        shoot("4b_add_income")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilAtLeastOneExists(hasTestTag("nav_settings"), timeoutMillis = 10_000)

        open("settings", "Monthly budget")
        shoot("5_settings")
    }
}

class LightScreenshotTest : AppScreenshotTest("")

/** Google Play phone screenshots: 1233×2193 (16:9, under Play's 2:1 limit). */
@Config(qualifiers = "w411dp-h731dp-xxhdpi")
class PlayStoreScreenshotTest : AppScreenshotTest(
    suffix = "",
    directory = "../docs/play-store/phone",
    only = setOf("1_home", "9_cash_flow", "9b_cash_flow_charts", "2_analytics", "2b_analytics_charts", "3_history", "4_add_expense", "4b_add_income"),
)

@Config(qualifiers = "+night")
class DarkScreenshotTest : AppScreenshotTest("_dark")
