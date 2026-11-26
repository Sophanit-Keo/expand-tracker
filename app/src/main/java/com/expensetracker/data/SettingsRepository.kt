package com.expensetracker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Currencies offered in Settings.
 *
 * @param decimals number of fraction digits normally shown for the currency.
 * @param symbolFirst whether the symbol is written before the amount ("$5") or after ("5៛").
 * @param sampleScale rough value of 1 USD in this currency, used only to make sample data realistic.
 */
enum class AppCurrency(
    val displayName: String,
    val symbol: String,
    val decimals: Int,
    val symbolFirst: Boolean = true,
    val sampleScale: Double = 1.0,
) {
    USD("US Dollar", "$", 2),
    EUR("Euro", "€", 2, sampleScale = 0.92),
    GBP("British Pound", "£", 2, sampleScale = 0.79),
    KHR("Cambodian Riel", "៛", 0, symbolFirst = false, sampleScale = 4000.0),
    THB("Thai Baht", "฿", 2, sampleScale = 35.0),
    VND("Vietnamese Dong", "₫", 0, symbolFirst = false, sampleScale = 25000.0),
    SGD("Singapore Dollar", "S$", 2, sampleScale = 1.35),
    JPY("Japanese Yen", "¥", 0, sampleScale = 150.0),
    KRW("South Korean Won", "₩", 0, sampleScale = 1350.0),
    CNY("Chinese Yuan", "CN¥", 2, sampleScale = 7.2),
    INR("Indian Rupee", "₹", 2, sampleScale = 83.0),
    AUD("Australian Dollar", "A$", 2, sampleScale = 1.5),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class UserSettings(
    val currency: AppCurrency = AppCurrency.USD,
    /** Monthly spending budget; 0 means "no budget set". */
    val monthlyBudget: Double = 0.0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** First-run setup finished (or skipped because there was already data). */
    val onboarded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val currency = stringPreferencesKey("currency")
        val budget = doublePreferencesKey("monthly_budget")
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun setCurrency(currency: AppCurrency) = context.dataStore.edit { it[Keys.currency] = currency.name }

    suspend fun setMonthlyBudget(amount: Double) = context.dataStore.edit { it[Keys.budget] = amount.coerceAtLeast(0.0) }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }

    suspend fun setOnboarded(done: Boolean = true) = context.dataStore.edit { it[Keys.onboarded] = done }

    private fun Preferences.toSettings() = UserSettings(
        currency = enumValueOrDefault(this[Keys.currency], AppCurrency.USD),
        monthlyBudget = this[Keys.budget] ?: 0.0,
        themeMode = enumValueOrDefault(this[Keys.theme], ThemeMode.SYSTEM),
        dynamicColor = this[Keys.dynamicColor] ?: false,
        onboarded = this[Keys.onboarded] ?: false,
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
