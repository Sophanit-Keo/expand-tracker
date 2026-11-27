package com.expensetracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Market rates: how many units of each currency one unit of [base] buys, and who published them. */
data class MarketRates(
    val base: AppCurrency,
    val perBase: Map<AppCurrency, Double>,
    val provider: String,
    val providerUrl: String,
)

/** Where today's exchange rates come from. */
fun interface RateSource {
    suspend fun latest(base: AppCurrency): MarketRates
}

/**
 * Free daily rates that need no account or key: ExchangeRate-API's open endpoint, with the
 * open-source currency-api (served by jsDelivr) as a fallback. Only the main currency's code is sent.
 */
class OnlineRateSource(private val timeoutMs: Int = 10_000) : RateSource {
    override suspend fun latest(base: AppCurrency): MarketRates = withContext(Dispatchers.IO) {
        try {
            parseExchangeRateApi(get(EXCHANGE_RATE_API + base.name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            parseCurrencyApi(get(currencyApiUrl(base)), base)
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("Response too large")
                }
                out.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_BYTES = 512 * 1024
        const val EXCHANGE_RATE_API = "https://open.er-api.com/v6/latest/"

        fun currencyApiUrl(base: AppCurrency) =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/${base.name.lowercase()}.json"

        /** `{"result":"success","base_code":"USD","rates":{"KHR":4067.5,…}}` */
        fun parseExchangeRateApi(json: String): MarketRates {
            val root = JSONObject(json)
            if (root.optString("result") != "success") throw IOException("Rates not available")
            val base = AppCurrency.entries.firstOrNull { it.name == root.getString("base_code") } ?: throw IOException("Unknown base")
            val rates = root.getJSONObject("rates")
            return MarketRates(base, read(base) { rates.optDouble(it.name) }, "ExchangeRate-API", "https://www.exchangerate-api.com")
        }

        /** `{"date":"2026-11-27","usd":{"khr":4069.0,…}}` */
        fun parseCurrencyApi(json: String, base: AppCurrency): MarketRates {
            val rates = JSONObject(json).getJSONObject(base.name.lowercase())
            return MarketRates(base, read(base) { rates.optDouble(it.name.lowercase()) }, "currency-api", "https://github.com/fawazahmed0/exchange-api")
        }

        private fun read(base: AppCurrency, value: (AppCurrency) -> Double): Map<AppCurrency, Double> {
            val result = AppCurrency.entries.mapNotNull { c ->
                (if (c == base) 1.0 else value(c)).takeIf { it.isFinite() && it > 0 }?.let { c to it }
            }.toMap()
            if (result.size < 2) throw IOException("No rates")
            return result
        }
    }
}
