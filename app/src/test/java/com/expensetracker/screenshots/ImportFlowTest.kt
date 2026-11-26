package com.expensetracker.screenshots

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.ExpenseApp
import com.expensetracker.MainActivity
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.Expense
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * End-to-end import: the file picker "returns" a CSV statement, the user reviews it and imports.
 * Also records screenshots of the import screens (./gradlew recordRoborazziDebug).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
class ImportFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<ExpenseApp>().container

    private val csv = """
        Account statement,,,,
        Date,Description,Money In,Money Out,Balance
        2026-11-03,POS PURCHASE STARBUCKS #1234,,5.75,994.25
        2026-11-04,SALARY NOVEMBER,"2,500.00",,"3,494.25"
        2026-11-05,GRAB RIDE 88123456,,3.20,"3,491.05"
        2026-11-06,LUCKY SUPERMARKET,,42.10,"3,448.95"
        2026-11-07,NETFLIX.COM,,15.99,"3,432.96"
        2026-11-08,ATM WITHDRAWAL XXXX1234,,100.00,"3,332.96"
        2026-11-09,LUNCH WITH TEAM,,12.00,"3,320.96"
    """.trimIndent()

    @Before
    fun seed() {
        runBlocking {
            container.expenseRepository.clear()
            container.settingsRepository.setCurrency(AppCurrency.USD)
            // Already typed in by hand; the statement's 12.00 on Nov 9 should be flagged as a duplicate.
            container.expenseRepository.save(Expense(amount = 12.0, category = Category.FOOD, note = "Lunch", date = LocalDate.of(2026, 11, 9)))
        }
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    private fun pickFile(file: File) {
        compose.onNode(hasTestTag("choose_file")).performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            val shadow = shadowOf(activity)
            val request = shadow.nextStartedActivityForResult
            shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
    }

    @Test
    fun importCsvStatement() {
        val file = File(ApplicationProvider.getApplicationContext<ExpenseApp>().cacheDir, "november.csv").apply { writeText(csv) }

        compose.onNode(hasTestTag("nav_history")).performClick()
        compose.onNode(hasTestTag("history_import")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Import from your bank"), timeoutMillis = 10_000)
        shoot("6_import_start")

        pickFile(file)
        compose.waitUntilAtLeastOneExists(hasText("7 transactions found"), timeoutMillis = 15_000)
        shoot("7_import_review")

        // The 12.00 lunch duplicates a hand-entered expense: unticked. Everything else is selected:
        // Starbucks, Grab, Lucky, Netflix, ATM (spending) and the salary (income) = 6.
        compose.onNode(hasTestTag("import_confirm")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Imported 6 transactions"), timeoutMillis = 10_000)
        shoot("8_import_done")

        val imported = runBlocking { container.expenseRepository.getAll() }.filter { it.importKey != null }
        assertEquals(6, imported.size)
        val salary = imported.single { it.isIncome }
        assertEquals(Category.SALARY, salary.category)
        assertEquals(2500.0, salary.amount, 0.0)
        val byNote = imported.associateBy { it.note }
        assertEquals(Category.FOOD, byNote.getValue("Starbucks").category)
        assertEquals(Category.TRANSPORT, byNote.getValue("Grab Ride").category)
        assertEquals(Category.GROCERIES, byNote.getValue("Lucky Supermarket").category)
        assertEquals(Category.ENTERTAINMENT, byNote.getValue("Netflix.com").category)
        assertEquals(LocalDate.of(2026, 11, 3), byNote.getValue("Starbucks").date)
        assertEquals(5.75, byNote.getValue("Starbucks").amount, 0.0)

        // Importing the same file again finds nothing new.
        compose.onNode(hasText("Done")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("history_import"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("history_import")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Import from your bank"), timeoutMillis = 10_000)
        pickFile(file)
        compose.waitUntilAtLeastOneExists(hasText("Select transactions to import"), timeoutMillis = 15_000)
        // Nothing new: every row is already imported (or the hand-entered duplicate), so the button is off.
        compose.onNode(hasTestTag("import_confirm")).assertIsNotEnabled()
        assertTrue(compose.onAllNodes(hasText("Already imported")).fetchSemanticsNodes().isNotEmpty())
    }
}
