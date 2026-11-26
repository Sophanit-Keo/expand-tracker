package com.expensetracker.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.ExpenseApp
import com.expensetracker.MainActivity
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** End-to-end: add an expense through the real UI and check it is stored and listed. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h880dp")
class AddExpenseFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = ApplicationProvider.getApplicationContext<ExpenseApp>().container

    @Before
    fun reset() {
        runBlocking {
            container.expenseRepository.clear()
            container.settingsRepository.setCurrency(AppCurrency.USD)
        }
    }

    @Test
    fun addExpenseShowsUpInHistory() {
        compose.waitUntilAtLeastOneExists(hasText("Start tracking your spending"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("nav_history")).performClick()
        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New expense"), timeoutMillis = 10_000)

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("12.5")
        compose.onNodeWithText("Transport").performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Taxi home")
        compose.onNodeWithText("Save expense").performClick()

        // Back on History once the editor has closed, with the new row listed.
        compose.waitUntilDoesNotExist(hasText("New expense"), timeoutMillis = 10_000)
        compose.waitUntilAtLeastOneExists(hasText("-$12.50"), timeoutMillis = 10_000)
        compose.onNodeWithText("Taxi home").assertExists()

        val saved = runBlocking { container.expenseRepository.getAll() }
        assertEquals(1, saved.size)
        assertEquals(12.5, saved.single().amount, 0.0)
        assertEquals(Category.TRANSPORT, saved.single().category)
        assertEquals("Taxi home", saved.single().note)
    }

    @Test
    fun addIncomeIsStoredAsIncome() {
        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New expense"), timeoutMillis = 10_000)
        compose.onNode(hasTestTag("type_income")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New income"), timeoutMillis = 10_000)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("1500")
        compose.onNodeWithText("Save income").performClick()
        compose.waitUntilDoesNotExist(hasText("New income"), timeoutMillis = 10_000)

        val saved = runBlocking { container.expenseRepository.getAll() }.single()
        assertEquals(com.expensetracker.data.EntryType.INCOME, saved.type)
        assertEquals(Category.SALARY, saved.category)
        assertEquals(1500.0, saved.amount, 0.0)
    }

    @Test
    fun saveWithoutAmountShowsError() {
        compose.onNode(hasTestTag("add_expense")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("New expense"), timeoutMillis = 10_000)
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Enter an amount greater than zero"), timeoutMillis = 5_000)
        assertEquals(0, runBlocking { container.expenseRepository.getAll() }.size)
    }
}
