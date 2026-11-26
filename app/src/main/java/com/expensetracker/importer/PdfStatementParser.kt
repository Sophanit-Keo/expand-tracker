package com.expensetracker.importer

import java.time.LocalDate
import kotlin.math.abs

/** A word on a PDF page with its horizontal extent (in PDF points). */
data class PdfWord(val text: String, val x0: Float, val x1: Float) {
    val center: Float get() = (x0 + x1) / 2
}

/** Words that share a baseline, left to right. */
data class PdfLine(val page: Int, val y: Float, val words: List<PdfWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

data class PdfParseResult(
    val transactions: List<ParsedTransaction>,
    /** True when a column header (Debit/Credit/Balance…) was found and used. */
    val usedColumnHeader: Boolean,
)

/**
 * Finds transactions in the text of a PDF statement. A transaction line starts with a date and ends
 * with one or more amounts. Which amount is spending is decided, in order of reliability, by:
 * the column it sits under (Money out / Debit / Withdrawal…), DR/CR or minus markers, the change in
 * the running balance, and finally words such as "salary" or "purchase".
 */
object PdfStatementParser {
    private enum class Col { DEBIT, CREDIT, AMOUNT, BALANCE }

    private data class Column(val role: Col, val x0: Float, val x1: Float) {
        val center get() = (x0 + x1) / 2
    }

    private val headerPhrases: List<Pair<String, Col?>> = listOf(
        "money out" to Col.DEBIT, "paid out" to Col.DEBIT, "withdrawal" to Col.DEBIT, "withdrawals" to Col.DEBIT,
        "debit" to Col.DEBIT, "debits" to Col.DEBIT, "dr" to Col.DEBIT, "out" to Col.DEBIT,
        "money in" to Col.CREDIT, "paid in" to Col.CREDIT, "deposit" to Col.CREDIT, "deposits" to Col.CREDIT,
        "credit" to Col.CREDIT, "credits" to Col.CREDIT, "cr" to Col.CREDIT, "in" to Col.CREDIT,
        "amount" to Col.AMOUNT, "transaction amount" to Col.AMOUNT,
        "balance" to Col.BALANCE, "running balance" to Col.BALANCE,
    )
    private val headerContext = setOf("date", "description", "details", "particulars", "narration", "transaction", "remarks", "reference")

    private val openingBalance = Regex("""(?i)(opening|beginning|previous|starting)\s+balance|balance\s+(brought\s+)?(forward|b/?f)|brought\s+forward""")
    private val nonTransaction = Regex("""(?i)^(total|closing balance|ending balance|balance carried|carried forward|sub-?total)""")
    private val markers = setOf("DR", "CR", "Dr", "Cr", "D", "C")
    private val currencyCodes = Regex("""^([A-Z]{3}|[$€£¥៛฿₫₩])$""")
    private val fullYear = Regex("""(19|20)\d{2}""")
    private val rowNumber = Regex("""^\d{1,4}\.?$""")

    private val outWords = Regex("""(?i)\b(purchase|pos|payment|paid|atm|withdraw\w*|debit|transfer to|bill|fee|charge|cash out|to account)\b""")
    private val inWords = Regex("""(?i)\b(deposit|salary|payroll|received|receive|transfer from|credit|interest|refund|cash in|incoming|from account|reversal)\b""")

    private data class Money(val value: Double, val word: PdfWord, val marker: String?)

    /** Dominant date layout of the statement (so 03/11 is read consistently as 3 Nov or as Mar 11). */
    private var preferredFormat: String? = null

    private fun detectDateFormat(lines: List<PdfLine>): String? {
        val leading = lines.mapNotNull { line ->
            val w = line.words.map { it.text }
            (3 downTo 1).firstNotNullOfOrNull { n ->
                if (n <= w.size) w.take(n).joinToString(" ").takeIf { DateParser.parseAny(it) != null } else null
            }
        }
        return DateParser.detectFormat(leading)
    }

    private data class RawTx(
        val date: LocalDate,
        var description: String,
        val amounts: List<Money>,
        val columns: List<Column>,
        val lineY: Float,
        val page: Int,
        var continuationLines: Int = 0,
    )

    fun parse(lines: List<PdfLine>): PdfParseResult {
        val reference = referenceDate(lines)
        preferredFormat = detectDateFormat(lines)
        var columns = emptyList<Column>()
        var usedHeader = false
        var openingBalanceValue: Double? = null
        val raw = mutableListOf<RawTx>()

        for (line in lines) {
            val words = line.words
            if (words.isEmpty()) continue
            headerColumns(words)?.let { columns = it; continue }

            val dateMatch = leadingDate(words, reference)
            val text = line.text
            if (dateMatch == null) {
                if (openingBalance.containsMatchIn(text)) {
                    trailingAmounts(words, 0, columns).lastOrNull()?.let { openingBalanceValue = it.value }
                    continue
                }
                // A wrapped description: no date, no amounts, right under a transaction.
                val last = raw.lastOrNull()
                if (last != null && last.page == line.page && last.continuationLines < 2 &&
                    line.y - last.lineY < 40f && trailingAmounts(words, 0, columns).isEmpty() &&
                    !nonTransaction.containsMatchIn(text) && words.size <= 12
                ) {
                    last.description = (last.description + " " + text).trim()
                    last.continuationLines++
                }
                continue
            }

            val (date, used) = dateMatch
            val rest = words.drop(used)
            val restText = rest.joinToString(" ") { it.text }
            if (openingBalance.containsMatchIn(restText)) {
                trailingAmounts(rest, 0, columns).lastOrNull()?.let { openingBalanceValue = it.value }
                continue
            }
            if (nonTransaction.containsMatchIn(restText)) continue
            val amounts = trailingAmounts(rest, 0, columns)
            if (amounts.isEmpty()) continue
            val descriptionWords = rest.take(rest.indexOf(amounts.first().word))
                .dropLastWhile { it.text in markers || currencyCodes.matches(it.text) }
            raw += RawTx(date, descriptionWords.joinToString(" ") { it.text }.trim(), amounts, columns, line.y, line.page)
            if (columns.isNotEmpty()) usedHeader = true
        }

        return PdfParseResult(resolve(raw, openingBalanceValue), usedHeader)
    }

    /** The latest full date printed anywhere: used to give a year to dates like "26 Nov". */
    private fun referenceDate(lines: List<PdfLine>): LocalDate {
        val dates = lines.flatMap { line ->
            val w = line.words.map { it.text }
            w.indices.filter { i -> fullYear.containsMatchIn(w[i]) }.flatMap { i ->
                // The year is the last word of the date: try it alone and with 1–2 words before it.
                (0..2).mapNotNull { back -> if (i - back >= 0) DateParser.parseAny(w.subList(i - back, i + 1).joinToString(" ")) else null }
            }
        }
        return dates.filter { it.year in 2000..2100 }.maxOrNull() ?: LocalDate.now()
    }

    private fun leadingDate(words: List<PdfWord>, reference: LocalDate): Pair<LocalDate, Int>? {
        var start = 0
        if (words.size > 1 && rowNumber.matches(words[0].text) && leadingDateAt(words, 1, reference) != null) start = 1
        val first = leadingDateAt(words, start, reference) ?: return null
        var used = start + first.second
        // A second date right after (value date) belongs to the same transaction.
        leadingDateAt(words, used, reference)?.let { used += it.second }
        return first.first to used
    }

    private fun leadingDateAt(words: List<PdfWord>, start: Int, reference: LocalDate): Pair<LocalDate, Int>? {
        for (n in 4 downTo 1) {
            if (start + n > words.size) continue
            val candidate = words.subList(start, start + n).joinToString(" ") { it.text }
            if (candidate.length < 4 && n == 1) continue
            if (candidate.none(Char::isDigit)) continue
            val date = preferredFormat?.let { DateParser.parse(candidate, it) }
                ?: DateParser.parseAny(candidate)
                ?: DateParser.parseYearless(candidate, reference)
            if (date != null) return date to n
        }
        return null
    }

    /** A header line names at least two money columns (or one plus "date"/"description"). */
    private fun headerColumns(words: List<PdfWord>): List<Column>? {
        val lower = words.map { it.text.lowercase().trim(':', '.', '(', ')') }
        val found = mutableListOf<Column>()
        var i = 0
        var context = 0
        while (i < words.size) {
            val two = if (i + 1 < words.size) "${lower[i]} ${lower[i + 1]}" else null
            val twoMatch = two?.let { t -> headerPhrases.firstOrNull { it.first == t } }
            val oneMatch = headerPhrases.firstOrNull { it.first == lower[i] }
            when {
                twoMatch != null -> { found += Column(twoMatch.second!!, words[i].x0, words[i + 1].x1); i += 2 }
                oneMatch != null -> { found += Column(oneMatch.second!!, words[i].x0, words[i].x1); i++ }
                else -> { if (lower[i] in headerContext) context++; i++ }
            }
        }
        val moneyCols = found.map { it.role }.toSet()
        val isHeader = (moneyCols.size >= 2 && context >= 1) || (moneyCols.size >= 1 && context >= 2) || moneyCols.size >= 3
        if (words.any { AmountParser.looksLikeMoney(it.text) }) return null
        // Header words are short labels, not sentences.
        return if (isHeader && words.size <= 14) found else null
    }

    /** Amount tokens at the end of the line (with any DR/CR marker or currency code next to them). */
    private fun trailingAmounts(words: List<PdfWord>, from: Int, columns: List<Column>): List<Money> {
        val result = mutableListOf<Money>()
        var i = words.size - 1
        var pendingMarker: String? = null
        while (i >= from) {
            val t = words[i].text
            when {
                t in markers -> pendingMarker = t
                currencyCodes.matches(t) -> Unit
                AmountParser.looksLikeMoney(t) || (columns.isNotEmpty() && underMoneyColumn(words[i], columns) && AmountParser.parse(t) != null && t.any(Char::isDigit) && t.length <= 15) -> {
                    var value = AmountParser.parse(t) ?: break
                    if (pendingMarker?.startsWith("D") == true) value = -abs(value)
                    result.add(0, Money(value, words[i], pendingMarker))
                    pendingMarker = null
                }
                else -> break
            }
            i--
        }
        return result
    }

    private fun underMoneyColumn(word: PdfWord, columns: List<Column>): Boolean =
        columns.any { word.x1 >= it.x0 - 25f && word.x0 <= it.x1 + 25f }

    private fun nearestColumn(word: PdfWord, columns: List<Column>): Col? =
        columns.minByOrNull { minOf(abs(word.center - it.center), abs(word.x1 - it.x1), abs(word.x0 - it.x0)) }?.role

    private fun resolve(raw: List<RawTx>, opening: Double?): List<ParsedTransaction> {
        data class Resolved(val tx: RawTx, val amount: Double, var direction: Direction, val balance: Double?, val explicit: Boolean)

        val resolved = raw.map { tx ->
            var amount: Double? = null
            var direction = Direction.UNKNOWN
            var balance: Double? = null
            var explicit = false
            if (tx.columns.isNotEmpty()) {
                for (m in tx.amounts) {
                    when (nearestColumn(m.word, tx.columns)) {
                        Col.DEBIT -> if (m.value != 0.0) { amount = abs(m.value); direction = Direction.OUT; explicit = true }
                        Col.CREDIT -> if (m.value != 0.0) { amount = abs(m.value); direction = Direction.IN; explicit = true }
                        Col.AMOUNT -> if (amount == null) amount = m.value
                        Col.BALANCE -> balance = m.value
                        null -> Unit
                    }
                }
            }
            if (amount == null) {
                val nonZero = tx.amounts.filter { it.value != 0.0 || it == tx.amounts.last() }
                if (nonZero.size >= 2) {
                    balance = nonZero.last().value
                    amount = nonZero[nonZero.size - 2].value
                } else {
                    amount = nonZero.firstOrNull()?.value
                }
            }
            val value = amount ?: return@map null
            val marker = tx.amounts.firstOrNull { abs(it.value) == abs(value) }?.marker
            if (!explicit) {
                when {
                    marker?.startsWith("D") == true || value < 0 -> { direction = Direction.OUT; explicit = true }
                    marker?.startsWith("C") == true -> { direction = Direction.IN; explicit = true }
                }
            }
            Resolved(tx, abs(value), direction, balance, explicit)
        }.filterNotNull()

        // Balance changes: walk in date order (statements may list newest first).
        val chronological = if (resolved.size > 1 && resolved.first().tx.date > resolved.last().tx.date) resolved.reversed() else resolved
        var previous = opening
        for (r in chronological) {
            val bal = r.balance
            if (r.direction == Direction.UNKNOWN && previous != null && bal != null) {
                r.direction = when {
                    abs(previous - r.amount - bal) < 0.011 -> Direction.OUT
                    abs(previous + r.amount - bal) < 0.011 -> Direction.IN
                    else -> Direction.UNKNOWN
                }
            }
            if (bal != null) previous = bal
        }

        return resolved.map { r ->
            val direction = if (r.direction != Direction.UNKNOWN) r.direction else when {
                inWords.containsMatchIn(r.tx.description) -> Direction.IN
                outWords.containsMatchIn(r.tx.description) -> Direction.OUT
                else -> Direction.UNKNOWN
            }
            ParsedTransaction(r.tx.date, r.tx.description, r.amount, direction)
        }
    }
}
