package com.expensetracker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expensetracker.domain.Exchange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Currencies offered in Settings.
 *
 * @param decimals number of fraction digits normally shown for the currency.
 * @param symbolFirst whether the symbol is written before the amount ("$5") or after ("5៛").
 * @param perUsd rough value of 1 USD in this currency. Only a suggestion when you add an exchange rate
 *   (you always enter the real rate), and it decides which way round a rate reads: "1 USD = 4,100 ៛".
 */
enum class AppCurrency(
    val displayName: String,
    val symbol: String,
    val decimals: Int,
    val symbolFirst: Boolean = true,
    val perUsd: Double = 1.0,
) {
    USD("US Dollar", "$", 2),
    EUR("Euro", "€", 2, perUsd = 0.92),
    GBP("British Pound", "£", 2, perUsd = 0.79),
    KHR("Cambodian Riel", "៛", 0, symbolFirst = false, perUsd = 4000.0),
    THB("Thai Baht", "฿", 2, perUsd = 35.0),
    VND("Vietnamese Dong", "₫", 0, symbolFirst = false, perUsd = 25000.0),
    SGD("Singapore Dollar", "S$", 2, perUsd = 1.35),
    JPY("Japanese Yen", "¥", 0, perUsd = 150.0),
    KRW("South Korean Won", "₩", 0, perUsd = 1350.0),
    CNY("Chinese Yuan", "CN¥", 2, perUsd = 7.2),
    INR("Indian Rupee", "₹", 2, perUsd = 83.0),
    AUD("Australian Dollar", "A$", 2, perUsd = 1.5),
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
    /** Saved exchange rates: how much of the main [currency] one unit of each other currency is worth. */
    val rates: Map<AppCurrency, Double> = emptyMap(),
    /** Download today's rates from the internet (once or twice a day). */
    val autoRates: Boolean = true,
    /** When rates were last downloaded (epoch millis), 0 if never or since the main currency changed. */
    val ratesUpdatedAt: Long = 0,
    /** Currencies whose saved rate came from the internet rather than being typed in. */
    val onlineRates: Set<AppCurrency> = emptySet(),
    val ratesProvider: String = "",
    val ratesProviderUrl: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val currency = stringPreferencesKey("currency")
        val budget = doublePreferencesKey("monthly_budget")
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val onboarded = booleanPreferencesKey("onboarded")
        val rates = stringPreferencesKey("exchange_rates")
        val autoRates = booleanPreferencesKey("auto_rates")
        val ratesUpdatedAt = longPreferencesKey("rates_updated_at")
        val onlineRates = stringPreferencesKey("online_rates")
        val ratesProvider = stringPreferencesKey("rates_provider")
        val ratesProviderUrl = stringPreferencesKey("rates_provider_url")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    /** Changes the main currency; saved exchange rates are re-expressed in it where possible. */
    suspend fun setCurrency(currency: AppCurrency) = context.dataStore.edit {
        val old = enumValueOrDefault(it[Keys.currency], AppCurrency.USD)
        it[Keys.currency] = currency.name
        it[Keys.rates] = Exchange.encode(Exchange.rebase(Exchange.decode(it[Keys.rates]), old, currency))
        // Download fresh rates against the new main currency next time.
        if (old != currency) it[Keys.ratesUpdatedAt] = 0L
    }

    /** Saves the rate for [currency]: one unit of it is worth [rate] of the main currency. */
    suspend fun setRate(currency: AppCurrency, rate: Double) = context.dataStore.edit {
        if (rate.isFinite() && rate > 0 && currency.name != (it[Keys.currency] ?: AppCurrency.USD.name)) {
            it[Keys.rates] = Exchange.encode(Exchange.decode(it[Keys.rates]) + (currency to rate))
            // Typed in by you: no longer the downloaded rate.
            it[Keys.onlineRates] = encodeSet(decodeSet(it[Keys.onlineRates]) - currency)
        }
    }

    /** Saves downloaded [rates] (main per one unit), unless the main currency changed meanwhile. */
    suspend fun saveOnlineRates(base: AppCurrency, rates: Map<AppCurrency, Double>, at: Long, provider: String, providerUrl: String) =
        context.dataStore.edit {
            if ((it[Keys.currency] ?: AppCurrency.USD.name) != base.name) return@edit
            val valid = rates.filter { (c, r) -> c != base && r.isFinite() && r > 0 }
            it[Keys.rates] = Exchange.encode(Exchange.decode(it[Keys.rates]) + valid)
            it[Keys.onlineRates] = encodeSet(decodeSet(it[Keys.onlineRates]) + valid.keys)
            it[Keys.ratesUpdatedAt] = at
            it[Keys.ratesProvider] = provider
            it[Keys.ratesProviderUrl] = providerUrl
        }

    suspend fun setAutoRates(enabled: Boolean) = context.dataStore.edit { it[Keys.autoRates] = enabled }

    suspend fun removeRate(currency: AppCurrency) = context.dataStore.edit {
        it[Keys.rates] = Exchange.encode(Exchange.decode(it[Keys.rates]) - currency)
        it[Keys.onlineRates] = encodeSet(decodeSet(it[Keys.onlineRates]) - currency)
    }

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
        rates = Exchange.decode(this[Keys.rates]),
        autoRates = this[Keys.autoRates] ?: true,
        ratesUpdatedAt = this[Keys.ratesUpdatedAt] ?: 0L,
        onlineRates = decodeSet(this[Keys.onlineRates]),
        ratesProvider = this[Keys.ratesProvider].orEmpty(),
        ratesProviderUrl = this[Keys.ratesProviderUrl].orEmpty(),
    )
}

private fun encodeSet(set: Set<AppCurrency>): String = set.sortedBy { it.ordinal }.joinToString(",") { it.name }

private fun decodeSet(text: String?): Set<AppCurrency> =
    text.orEmpty().split(',').mapNotNull { code -> AppCurrency.entries.firstOrNull { it.name == code } }.toSet()

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
