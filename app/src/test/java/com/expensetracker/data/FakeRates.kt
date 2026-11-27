package com.expensetracker.data

import java.io.IOException

/** Rates for tests: [perUsd] units of each currency per US dollar, crossed for any base. Counts calls. */
class FixedRates(private val perUsd: Map<AppCurrency, Double> = DEFAULT) : RateSource {
    var calls = 0
        private set

    override suspend fun latest(base: AppCurrency): MarketRates {
        calls++
        val baseInUsd = perUsd.getValue(base)
        return MarketRates(base, perUsd.mapValues { (_, v) -> v / baseInUsd }, "ExchangeRate-API", "https://www.exchangerate-api.com")
    }

    companion object {
        val DEFAULT = AppCurrency.entries.associateWith { it.perUsd } + mapOf(AppCurrency.KHR to 4100.0, AppCurrency.THB to 36.0)
    }
}

/** No network: every download fails, as on a phone in flight mode. */
class OfflineRates : RateSource {
    var calls = 0
        private set

    override suspend fun latest(base: AppCurrency): MarketRates {
        calls++
        throw IOException("offline")
    }
}
