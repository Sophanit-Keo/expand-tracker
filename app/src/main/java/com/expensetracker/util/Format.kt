package com.expensetracker.util

import com.expensetracker.data.AppCurrency
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

private val symbols = DecimalFormatSymbols(Locale.US)

private fun pattern(decimals: Int) = if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals)

private fun withSymbol(number: String, currency: AppCurrency, negative: Boolean = false): String {
    val sign = if (negative) "-" else ""
    return if (currency.symbolFirst) "$sign${currency.symbol}$number" else "$sign$number${currency.symbol}"
}

/** "$1,234.50", "12,000៛". */
fun formatMoney(amount: Double, currency: AppCurrency): String {
    val number = DecimalFormat(pattern(currency.decimals), symbols).format(abs(amount))
    return withSymbol(number, currency, negative = amount < 0)
}

/** Short form for chart labels: "$950", "$1.2K", "4.5M៛". */
fun formatCompact(amount: Double, currency: AppCurrency): String {
    val value = abs(amount)
    val (scaled, suffix) = when {
        value >= 1_000_000_000 -> value / 1_000_000_000 to "B"
        value >= 1_000_000 -> value / 1_000_000 to "M"
        value >= 1_000 -> value / 1_000 to "K"
        else -> value to ""
    }
    val number = when {
        suffix.isEmpty() -> DecimalFormat("#,##0", symbols).format(scaled)
        scaled >= 100 -> DecimalFormat("0", symbols).format(scaled)
        else -> DecimalFormat("0.#", symbols).format(scaled)
    } + suffix
    return withSymbol(number, currency, negative = amount < 0)
}

/** Keeps only what can be typed into an amount field: digits and at most one decimal separator. */
fun sanitizeAmountInput(input: String, decimals: Int, maxLength: Int = 12): String {
    val cleaned = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    if (decimals == 0) return cleaned.filter(Char::isDigit).take(maxLength)
    val dot = cleaned.indexOf('.')
    val result = if (dot == -1) {
        cleaned
    } else {
        cleaned.substring(0, dot + 1) + cleaned.substring(dot + 1).replace(".", "").take(decimals)
    }
    return result.take(maxLength)
}

/** Amount as editable text, without trailing zeros: 12.5 -> "12.5", 3000.0 -> "3000". */
fun amountToInput(amount: Double): String = BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
