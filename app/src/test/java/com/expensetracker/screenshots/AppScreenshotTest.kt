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
abstract class AppScreenshotTest(private val suffix: String) {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() {
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
        compose.onRoot().captureRoboImage("../docs/screenshots/$name$suffix.png")
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
        compose.waitUntilAtLeastOneExists(hasText("Last 7 days"), timeoutMillis = 10_000)
        shoot("1_home")
        scrollTo("Recent transactions")
        shoot("1b_home_scrolled")

        open("analytics", "By category")
        shoot("2_analytics")
        scrollTo("Spending over time")
        shoot("2b_analytics_charts")
        scrollTo("Insights")
        shoot("2c_analytics_insights")

        open("history", "Search notes or categories")
        shoot("3_history")

        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New expense"), timeoutMillis = 10_000)
        shoot("4_add_expense")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilAtLeastOneExists(hasTestTag("nav_settings"), timeoutMillis = 10_000)

        open("settings", "Monthly budget")
        shoot("5_settings")
    }
}

class LightScreenshotTest : AppScreenshotTest("")

@Config(qualifiers = "+night")
class DarkScreenshotTest : AppScreenshotTest("_dark")
