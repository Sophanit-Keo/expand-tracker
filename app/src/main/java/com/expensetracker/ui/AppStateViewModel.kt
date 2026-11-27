package com.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

data class AppState(val settings: UserSettings, val showOnboarding: Boolean)

/** App-wide state: settings for the theme, and whether to show first-run setup. */
class AppStateViewModel(
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Null until settings and data have loaded; the splash screen stays up until then. */
    val state: StateFlow<AppState?> = combine(
        settings.settings,
        expenses.expenses.map { it.isNotEmpty() }.distinctUntilChanged(),
    ) { prefs, hasData -> AppState(prefs, showOnboarding = !prefs.onboarded && !hasData) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // People who used the app before setup existed already have data: don't greet them again.
        viewModelScope.launch {
            state.filterNotNull().collect { s ->
                if (!s.settings.onboarded && !s.showOnboarding) settings.setOnboarded()
            }
        }
    }

    fun finishOnboarding(currency: AppCurrency, monthlyBudget: Double) {
        viewModelScope.launch {
            settings.setCurrency(currency)
            settings.setMonthlyBudget(monthlyBudget)
            settings.setOnboarded()
        }
    }

    companion object {
        /** The currency of the phone's region, if the app supports it. */
        fun guessCurrency(locale: Locale = Locale.getDefault()): AppCurrency =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
                ?.let { code -> AppCurrency.entries.firstOrNull { it.name == code } }
                ?: AppCurrency.USD
    }
}
