package com.expensetracker.domain

import com.expensetracker.data.AppCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * A rate written the way people say it, "1 USD = 4,100 ៛": [one] unit of the currency worth more
 * equals [value] of the [other].
 */
data class RateQuote(val one: AppCurrency, val other: AppCurrency, val value: Double)

/**
 * Exchange rates between the main currency and the others. A rate is stored as "main-currency
 * units per one unit of the other currency", so converting is a single multiplication.
 */
object Exchange {
    /** A rough rate from the built-in table: only a placeholder and a way to pick the direction. */
    fun approximate(foreign: AppCurrency, main: AppCurrency): Double = main.perUsd / foreign.perUsd

    /** Whether people write this pair as "1 foreign = x main" (the foreign currency is worth more). */
    fun foreignFirst(foreign: AppCurrency, main: AppCurrency): Boolean = approximate(foreign, main) >= 1.0

    /** [rate] (main per one [foreign]) the way it's usually written. */
    fun quote(foreign: AppCurrency, main: AppCurrency, rate: Double): RateQuote =
        if (foreignFirst(foreign, main)) RateQuote(foreign, main, rate) else RateQuote(main, foreign, 1 / rate)

    /** The other way: from the number typed into "1 X = [value] Y" back to main per one [foreign]. */
    fun rateFromQuote(foreign: AppCurrency, main: AppCurrency, value: Double): Double? {
        if (!value.isFinite() || value <= 0) return null
        return if (foreignFirst(foreign, main)) value else 1 / value
    }

    /** [amount] of [currency] in the main currency, or null without a rate. */
    fun toMain(amount: Double, currency: AppCurrency, main: AppCurrency, rates: Map<AppCurrency, Double>): Double? =
        if (currency == main) amount else rates[currency]?.let { amount * it }

    /**
     * Saved rates re-expressed against a new main currency. Needs the rate between the old and the
     * new main currency; without it nothing can be converted and the rates are dropped.
     */
    fun rebase(rates: Map<AppCurrency, Double>, oldMain: AppCurrency, newMain: AppCurrency): Map<AppCurrency, Double> {
        if (oldMain == newMain) return rates
        val newInOld = rates[newMain] ?: return emptyMap() // 1 newMain = newInOld oldMain
        return buildMap {
            rates.forEach { (currency, rate) -> if (currency != newMain) put(currency, rate / newInOld) }
            put(oldMain, 1 / newInOld)
        }
    }

    fun encode(rates: Map<AppCurrency, Double>): String =
        rates.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.name}=${it.value}" }

    fun decode(text: String?): Map<AppCurrency, Double> =
        text.orEmpty().split(';').mapNotNull { part ->
            val (code, value) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val currency = AppCurrency.entries.firstOrNull { it.name == code } ?: return@mapNotNull null
            value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let { currency to it }
        }.toMap()

    /** A rate for reading: "4,100", "1.08", "0.0275". */
    fun formatRate(value: Double): String {
        val decimals = when {
            value >= 100 -> 2
            value >= 1 -> 4
            else -> 6
        }
        val pattern = "#,##0." + "#".repeat(decimals)
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
    }

    /** "1 USD = 4,100 KHR" for a saved [rate] (main per one [foreign]). */
    fun describe(foreign: AppCurrency, main: AppCurrency, rate: Double): String {
        val q = quote(foreign, main, rate)
        return "1 ${q.one.name} = ${formatRate(q.value)} ${q.other.name}"
    }

    /** A rate for an input field: no grouping, no trailing zeros. */
    fun rateToInput(value: Double): String =
        BigDecimal.valueOf(value).setScale(if (value >= 1) 4 else 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
