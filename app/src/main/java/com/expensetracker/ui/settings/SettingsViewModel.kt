package com.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SampleData
import com.expensetracker.data.SettingsRepository
import com.expensetracker.data.ThemeMode
import com.expensetracker.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val expenseCount: Int = 0,
)

class SettingsViewModel(
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(settings.settings, expenses.expenses) { prefs, all ->
        SettingsUiState(prefs, all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setCurrency(currency: AppCurrency) = viewModelScope.launch { settings.setCurrency(currency) }

    fun setBudget(amount: Double) = viewModelScope.launch { settings.setMonthlyBudget(amount) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    fun loadSampleData() = viewModelScope.launch {
        expenses.addAll(SampleData.generate(uiState.value.settings.currency))
    }

    fun clearAll() = viewModelScope.launch { expenses.clear() }

    /** All expenses as CSV text (newest first), ready to share. */
    suspend fun exportCsv(): String = buildCsv(expenses.getAll())

    companion object {
        fun buildCsv(items: List<Expense>): String = buildString {
            appendLine("date,category,amount,note")
            items.forEach { e ->
                append(e.date).append(',')
                append(csvField(e.category.label)).append(',')
                append(e.amount).append(',')
                appendLine(csvField(e.note))
            }
        }

        private fun csvField(value: String): String =
            if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
