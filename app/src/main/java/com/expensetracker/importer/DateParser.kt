package com.expensetracker.importer

import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A date layout the importer understands, e.g. "dd/MM/yyyy". */
data class DateFormat(val id: String, val example: String)

object DateParser {
    const val EXCEL_SERIAL = "excel-serial"

    /** Order matters: on a tie, earlier (day-first) formats win. */
    private val patterns = listOf(
        "yyyy-M-d", "yyyy/M/d", "yyyy.M.d",
        "d/M/yyyy", "M/d/yyyy", "d-M-yyyy", "M-d-yyyy", "d.M.yyyy",
        "d/M/yy", "M/d/yy", "d-M-yy", "d.M.yy",
        "d MMM yyyy", "d-MMM-yyyy", "d MMM, yyyy", "d/MMM/yyyy", "d MMM yy", "d-MMM-yy",
        "MMM d, yyyy", "MMM d yyyy", "MMM-d-yyyy",
        "d MMMM yyyy", "MMMM d, yyyy", "d MMMM, yyyy",
        "EEE, d MMM yyyy", "EEE d MMM yyyy",
        "yyyyMMdd",
    )

    /** Formats without a year, as printed on many PDF statements ("26 Nov", "26/11"). */
    private val yearlessPatterns = listOf("d MMM", "d-MMM", "MMM d", "d/M", "d MMMM", "MMMM d")

    private val formatters: Map<String, DateTimeFormatter> = patterns.associateWith(::formatter)
    private val yearlessFormatters: Map<String, DateTimeFormatter> = yearlessPatterns.associateWith { p ->
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)
    }

    val supportedFormats: List<DateFormat> = patterns.map { DateFormat(it, LocalDate.of(2026, 11, 26).format(formatters.getValue(it))) } +
        DateFormat(EXCEL_SERIAL, "Excel date number")

    private fun formatter(pattern: String): DateTimeFormatter {
        val builder = DateTimeFormatterBuilder().parseCaseInsensitive()
        if (pattern.contains("yy") && !pattern.contains("yyyy")) {
            // Two-digit years: 00–99 → 2000–2099.
            builder.appendPattern(pattern.replace("yy", "")).appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
        } else {
            builder.appendPattern(pattern.replace("yyyy", "uuuu"))
        }
        return builder.toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }

    private val timeSuffix = Regex("""[ T]\d{1,2}:\d{2}(:\d{2}(\.\d+)?)?(\s?[AaPp][Mm])?.*$""")

    private fun clean(raw: String): String =
        raw.trim().trim('"').replace(timeSuffix, "").replace(Regex("""\s+"""), " ").trim().trimEnd(',', '.')

    /** Cheap shape check so obviously non-date text never reaches the (exception-based) parsers. */
    private fun plausible(s: String) = s.length in 6..32 && s.any(Char::isDigit) && s.none { it == '$' || it == '@' }

    fun parse(raw: String?, formatId: String): LocalDate? {
        if (raw.isNullOrBlank()) return null
        if (formatId == EXCEL_SERIAL) return fromExcelSerial(raw)
        val formatter = formatters[formatId] ?: return null
        val cleaned = clean(raw)
        if (!plausible(cleaned)) return null
        return runCatching { LocalDate.parse(cleaned, formatter) }.getOrNull()
    }

    /** Excel stores dates as days since 1899-12-30. */
    fun fromExcelSerial(raw: String): LocalDate? {
        val n = raw.trim().toDoubleOrNull() ?: return null
        if (n !in 20_000.0..80_000.0) return null // ~1954..2119
        return LocalDate.of(1899, 12, 30).plusDays(n.toLong())
    }

    /** Tries every known format; returns the first match. Use [detectFormat] for whole columns. */
    fun parseAny(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val cleaned = clean(raw)
        if (!plausible(cleaned)) return null
        for (f in formatters.values) runCatching { return LocalDate.parse(cleaned, f) }
        return null
    }

    /**
     * Parses a date without a year ("26 Nov") using [reference] to pick the year: the latest date that
     * is not after the reference (statements list past transactions).
     */
    fun parseYearless(raw: String, reference: LocalDate): LocalDate? {
        val cleaned = clean(raw)
        if (cleaned.length !in 3..16 || cleaned.none(Char::isDigit)) return null
        for (f in yearlessFormatters.values) {
            val md = runCatching { MonthDay.parse(cleaned, f) }.getOrNull() ?: continue
            val sameYear = runCatching { md.atYear(reference.year) }.getOrNull() ?: continue
            return if (sameYear.isAfter(reference)) md.atYear(reference.year - 1) else sameYear
        }
        return null
    }

    /**
     * Picks the format that reads the most values in a column. Ties (e.g. 03/11/2026 reads as both
     * 3 Nov and 11 Mar) go to the format whose dates span the shortest time, since a statement covers
     * a short period; then to day-first.
     */
    fun detectFormat(values: List<String>): String? {
        val sample = values.filter { it.isNotBlank() }.take(400)
        if (sample.isEmpty()) return null
        data class Candidate(val id: String, val parsed: Int, val spanDays: Long, val order: Int)

        val candidates = (patterns + EXCEL_SERIAL).mapIndexedNotNull { index, id ->
            val dates = sample.mapNotNull { parse(it, id) }
            if (dates.isEmpty()) return@mapIndexedNotNull null
            Candidate(id, dates.size, ChronoUnit.DAYS.between(dates.min(), dates.max()), index)
        }
        val best = candidates.maxOfOrNull { it.parsed } ?: return null
        if (best < (sample.size * 0.5)) return null
        return candidates.filter { it.parsed == best }
            .minWith(compareBy<Candidate> { it.spanDays }.thenBy { it.order })
            .id
    }

    fun example(formatId: String): String = supportedFormats.firstOrNull { it.id == formatId }?.example ?: formatId
}
