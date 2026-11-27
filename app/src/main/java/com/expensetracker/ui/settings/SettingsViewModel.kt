package com.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Expense
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.data.ThemeMode
import com.expensetracker.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val expenseCount: Int = 0,
    val sampleCount: Int = 0,
    val importedCount: Int = 0,
)

class SettingsViewModel(
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(settings.settings, expenses.expenses) { prefs, all ->
        SettingsUiState(prefs, all.size, all.count { it.isSample }, all.count { it.importKey != null && !it.isSample })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setCurrency(currency: AppCurrency) = viewModelScope.launch { settings.setCurrency(currency) }

    fun setBudget(amount: Double) = viewModelScope.launch { settings.setMonthlyBudget(amount) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    fun clearAll() = viewModelScope.launch { expenses.clear() }

    fun removeSampleData() = viewModelScope.launch { expenses.removeSampleData() }

    fun removeImported() = viewModelScope.launch { expenses.removeImported() }

    /** All expenses as CSV text (newest first), ready to share. */
    suspend fun exportCsv(): String = buildCsv(expenses.getAll())

    companion object {
        fun buildCsv(items: List<Expense>): String = buildString {
            // amount is in the main currency; the last two columns are set for entries paid in another one.
            appendLine("date,type,category,amount,note,original_amount,original_currency")
            items.forEach { e ->
                append(e.date).append(',')
                append(if (e.isIncome) "income" else "expense").append(',')
                append(csvField(e.category.label)).append(',')
                append(BigDecimal.valueOf(e.amount).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()).append(',')
                append(csvField(e.note)).append(',')
                append(e.originalAmount?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()).append(',')
                appendLine(e.originalCurrency?.name.orEmpty())
            }
        }

        private fun csvField(value: String): String =
            if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
