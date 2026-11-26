package com.expensetracker.importer

/**
 * Parses money as banks print it: "1,234.56", "1.234,56", "(12.00)", "12.00-", "-$5", "USD 40,000",
 * "12.50 DR". Debit markers (minus, parentheses, trailing "-", "DR") make the result negative.
 */
object AmountParser {
    // A marker only counts right after the number ("12.50 DR", "12.50D"), never inside a code like "USD".
    private val debitSuffix = Regex("""(?<=[\d\s)])(DR|Dr|dr|D)\.?$""")
    private val creditSuffix = Regex("""(?<=[\d\s)])(CR|Cr|cr|C)\.?$""")
    private val groupedComma = Regex("""^\d{1,3}(,\d{3})+$""")
    private val groupedDot = Regex("""^\d{1,3}(\.\d{3})+$""")

    /** Strict money shape used to pick amounts out of free text (PDF lines): needs decimals or grouping. */
    private val moneyShape = Regex(
        """^[-−(]?(?:[A-Z]{3})?[$€£¥៛฿₫₩]?[-−]?(?:\d{1,3}(?:[,.  ]\d{3})+(?:[.,]\d{1,2})?|\d+[.,]\d{2})\)?-?(?:[A-Z]{3}|[$€£¥៛฿₫₩])?$""",
    )

    fun parse(raw: String?): Double? {
        if (raw == null) return null
        var s = raw.trim().replace('−', '-').replace(' ', ' ')
        if (s.isEmpty()) return null
        var negative = false

        debitSuffix.find(s)?.let { if (s.length > it.value.length) { negative = true; s = s.removeRange(it.range) } }
        creditSuffix.find(s)?.let { if (s.length > it.value.length) s = s.removeRange(it.range) }
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        // Drop currency codes and symbols, keep digits, separators and signs.
        s = s.replace(Regex("""[A-Za-z$€£¥៛฿₫₩'\s]"""), "")
        if (s.endsWith("-")) {
            negative = true
            s = s.dropLast(1)
        }
        if (s.startsWith("-")) {
            negative = true
            s = s.drop(1)
        }
        if (s.startsWith("+")) s = s.drop(1)
        if (s.isEmpty() || s.any { !it.isDigit() && it != ',' && it != '.' }) return null
        if (!s.any(Char::isDigit)) return null

        val normalized = normalizeSeparators(s) ?: return null
        val value = normalized.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    /** True when [token] looks like a printed money amount (not a date, reference or account number). */
    fun looksLikeMoney(token: String): Boolean {
        val t = token.trim()
        return moneyShape.matches(t) && parse(t) != null
    }

    private fun normalizeSeparators(s: String): String? {
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        return when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') // 1.234,56
                else s.replace(",", "") // 1,234.56
            lastComma >= 0 ->
                when {
                    groupedComma.matches(s) -> s.replace(",", "") // 40,000
                    s.count { it == ',' } == 1 && s.length - lastComma - 1 in 1..2 -> s.replace(',', '.') // 12,5
                    else -> s.replace(",", "")
                }
            lastDot >= 0 ->
                if (s.count { it == '.' } > 1) {
                    if (groupedDot.matches(s)) s.replace(".", "") else null // 1.234.567
                } else {
                    s
                }
            else -> s
        }
    }
}
