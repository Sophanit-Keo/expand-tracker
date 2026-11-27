package com.expensetracker.importer

enum class AmountMode { SINGLE, DEBIT_CREDIT }

/** Which spreadsheet columns hold what. Column indexes are 0-based; -1 means "none". */
data class ColumnMapping(
    /** Index of the header row; data starts on the next row. -1 when the file has no header. */
    val headerRow: Int,
    val dateColumn: Int,
    val descriptionColumn: Int,
    val amountMode: AmountMode,
    val amountColumn: Int = -1,
    val debitColumn: Int = -1,
    val creditColumn: Int = -1,
    /** Optional "DR/CR" or "Debit/Credit" indicator column used with a single amount column. */
    val typeColumn: Int = -1,
    /** Single-amount files where spending is positive (typical for credit cards). */
    val expensesArePositive: Boolean = false,
    val dateFormat: String,
    /** Running balance column, used to double-check money in vs out. */
    val balanceColumn: Int = -1,
    /** Amounts written like "40.000" for forty thousand. */
    val dotThousands: Boolean = false,
)

object TableMapper {
    private enum class Role { DATE, DESCRIPTION, AMOUNT, DEBIT, CREDIT, TYPE, BALANCE }

    // Higher in each list = better match. Phrases are compared on lowercase words.
    private val keywords: Map<Role, List<String>> = mapOf(
        Role.DATE to listOf("transaction date", "trans date", "txn date", "tran date", "posting date", "post date", "booking date", "date", "value date"),
        Role.DESCRIPTION to listOf(
            "description", "transaction details", "details", "narration", "narrative", "particulars", "remarks", "remark",
            "memo", "payee", "merchant", "beneficiary", "transaction", "purpose", "notes", "reference",
        ),
        Role.DEBIT to listOf(
            "money out", "paid out", "cash out", "amount out", "withdrawal", "withdrawals", "withdrawal amount", "debit", "debits",
            "debit amount", "debited", "dr", "out", "outgoing", "spent", "paid", "payments", "withdrawn", "sent", "expense", "expenses",
        ),
        Role.CREDIT to listOf(
            "money in", "paid in", "cash in", "amount in", "deposit", "deposits", "deposit amount", "credit", "credits", "credit amount",
            "credited", "cr", "in", "incoming", "received", "receipts", "income",
        ),
        Role.AMOUNT to listOf("amount", "transaction amount", "amt", "value", "sum"),
        Role.TYPE to listOf("dr/cr", "cr/dr", "debit/credit", "d/c", "type", "transaction type", "dc"),
        Role.BALANCE to listOf("balance", "running balance", "available balance", "closing balance", "ledger balance"),
    )

    private fun words(cell: String): List<String> =
        cell.lowercase().replace(Regex("""\(.*?\)"""), " ").split(Regex("""[^a-z/]+""")).filter { it.isNotEmpty() }

    /** How well a header cell matches a role: higher is better, 0 = no match. */
    private fun score(cell: String, role: Role): Int {
        val cellWords = words(cell)
        if (cellWords.isEmpty()) return 0
        val joined = cellWords.joinToString(" ")
        val list = keywords.getValue(role)
        list.forEachIndexed { i, keyword ->
            if (joined == keyword) return 1000 - i
        }
        list.forEachIndexed { i, keyword ->
            val kw = keyword.split(' ')
            if (kw.size > 1 && joined.contains(keyword)) return 500 - i
            if (kw.size == 1 && keyword.length > 2 && keyword in cellWords) return 400 - i
            // Short words like "in" only count in short labels ("Cash In", "In (USD)").
            if (kw.size == 1 && keyword.length <= 2 && cellWords.size <= 2 && keyword in cellWords) return 300 - i
        }
        return 0
    }

    private fun roleOf(cell: String): Role? = Role.entries
        .map { it to score(cell, it) }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first

    fun findHeaderRow(rows: List<List<String>>): Int {
        var best = -1
        var bestScore = 1
        rows.take(40).forEachIndexed { index, row ->
            val roles = row.mapNotNull(::roleOf).toSet()
            val hasMoney = Role.AMOUNT in roles || Role.DEBIT in roles || Role.CREDIT in roles
            val score = roles.size + if (Role.DATE in roles && hasMoney) 2 else 0
            if (score > bestScore) {
                best = index
                bestScore = score
            }
        }
        return best
    }

    /** Best-guess mapping, or null when no date and amount columns can be found. */
    fun detect(rows: List<List<String>>): ColumnMapping? {
        val headerRow = findHeaderRow(rows)
        val width = rows.maxOfOrNull { it.size } ?: return null
        val data = rows.drop(headerRow + 1)
        if (data.isEmpty()) return null
        val sample = data.take(300)

        fun column(i: Int) = sample.map { it.getOrElse(i) { "" } }
        fun dateRatio(i: Int) = column(i).filter { it.isNotBlank() }.let { v -> if (v.isEmpty()) 0.0 else v.count { DateParser.parseAny(it) != null || DateParser.fromExcelSerial(it) != null } / v.size.toDouble() }
        fun numberRatio(i: Int) = column(i).filter { it.isNotBlank() }.let { v -> if (v.isEmpty()) 0.0 else v.count { AmountParser.parse(it) != null } / v.size.toDouble() }
        fun textLength(i: Int) = column(i).map { it.length }.average()

        var date = -1
        var description = -1
        var amount = -1
        var debit = -1
        var credit = -1
        var type = -1
        var balance = -1
        var onlyCredit = false
        if (headerRow >= 0) {
            val header = rows[headerRow]
            fun best(role: Role, exclude: Set<Int> = emptySet()) = header.indices
                .filter { it !in exclude }
                .map { it to score(header[it], role) }
                .filter { it.second > 0 && score(header[it.first], role) >= Role.entries.maxOf { r -> score(header[it.first], r) } }
                .maxByOrNull { it.second }?.first ?: -1
            date = best(Role.DATE)
            debit = best(Role.DEBIT)
            credit = best(Role.CREDIT)
            amount = best(Role.AMOUNT)
            type = best(Role.TYPE)
            balance = best(Role.BALANCE)
            description = best(Role.DESCRIPTION, setOf(date, debit, credit, amount, type, balance))
        }

        // Fill gaps from the data itself.
        if (date < 0) date = (0 until width).maxByOrNull(::dateRatio)?.takeIf { dateRatio(it) >= 0.6 } ?: -1
        if (date < 0) return null
        val numeric = (0 until width).filter { it != date && it != balance && numberRatio(it) >= 0.6 }
        if (amount < 0 && (debit < 0 || credit < 0)) {
            if (debit >= 0 || credit >= 0) {
                // Only one of debit/credit labelled: use it as the amount column, remembering which.
                onlyCredit = credit >= 0
                amount = maxOf(debit, credit).also { debit = -1; credit = -1 }
            } else {
                amount = numeric.firstOrNull() ?: return null
            }
        }
        if (description < 0) {
            val used = setOf(date, amount, debit, credit, type, balance)
            description = (0 until width).filter { it !in used && numberRatio(it) < 0.5 }.maxByOrNull(::textLength) ?: -1
        }

        val mode = if (debit >= 0 && credit >= 0) AmountMode.DEBIT_CREDIT else AmountMode.SINGLE
        val dateFormat = DateParser.detectFormat(column(date)) ?: return null
        val moneyColumns = listOf(amount, debit, credit, balance).filter { it >= 0 }
        val dotThousands = AmountParser.usesDotThousands(moneyColumns.flatMap { column(it) })
        val amounts = if (mode == AmountMode.SINGLE) column(amount).mapNotNull { AmountParser.parse(it, dotThousands) } else emptyList()
        return ColumnMapping(
            headerRow = headerRow,
            dateColumn = date,
            descriptionColumn = description,
            amountMode = mode,
            amountColumn = if (mode == AmountMode.SINGLE) amount else -1,
            debitColumn = debit,
            creditColumn = credit,
            typeColumn = if (mode == AmountMode.SINGLE) type else -1,
            // No negatives at all usually means charges are listed as positive numbers, unless the
            // only money column is labelled as money in.
            expensesArePositive = mode == AmountMode.SINGLE && type < 0 && !onlyCredit && amounts.isNotEmpty() && amounts.none { it < 0 },
            dateFormat = dateFormat,
            balanceColumn = balance,
            dotThousands = dotThousands,
        )
    }

    /**
     * Transactions plus the statement's own figures (opening/closing balance, printed totals),
     * with money in/out double-checked against the running balance when there is one.
     */
    fun read(rows: List<List<String>>, mapping: ColumnMapping): StatementRead =
        BalanceCheck.apply(StatementRead(apply(rows, mapping), summary(rows, mapping)))

    private fun summary(rows: List<List<String>>, mapping: ColumnMapping): StatementSummary {
        var opening: Double? = null
        var closing: Double? = null
        var printedIn: Double? = null
        var printedOut: Double? = null
        fun money(row: List<String>, column: Int) = row.getOrNull(column)?.let { AmountParser.parse(it, mapping.dotThousands) }
        fun lastMoney(row: List<String>) = row.asReversed().firstNotNullOfOrNull { cell ->
            cell.takeIf { AmountParser.parse(it, mapping.dotThousands) != null && DateParser.parseAny(it) == null }
                ?.let { AmountParser.parse(it, mapping.dotThousands) }
        }
        // Statements often put these lines above the header too ("Opening balance: 500.00").
        rows.forEach { row ->
            val text = row.joinToString(" ")
            val isTransaction = DateParser.parse(row.getOrElse(mapping.dateColumn) { "" }, mapping.dateFormat) != null &&
                !StatementWords.openingBalance.containsMatchIn(text) && !StatementWords.closingBalance.containsMatchIn(text)
            if (isTransaction) return@forEach
            when {
                StatementWords.openingBalance.containsMatchIn(text) ->
                    opening = money(row, mapping.balanceColumn) ?: lastMoney(row)
                StatementWords.closingBalance.containsMatchIn(text) ->
                    closing = money(row, mapping.balanceColumn) ?: lastMoney(row)
                StatementWords.totalIn.containsMatchIn(text) -> printedIn = lastMoney(row)?.let { kotlin.math.abs(it) }
                StatementWords.totalOut.containsMatchIn(text) -> printedOut = lastMoney(row)?.let { kotlin.math.abs(it) }
                StatementWords.total.containsMatchIn(text) && mapping.amountMode == AmountMode.DEBIT_CREDIT -> {
                    money(row, mapping.debitColumn)?.let { printedOut = kotlin.math.abs(it) }
                    money(row, mapping.creditColumn)?.let { printedIn = kotlin.math.abs(it) }
                }
            }
        }
        // An opening row inside the table: "Opening balance" (or simply a first row with a balance
        // but no amount, like "01/11, Opening, 0, 500.00").
        val dataRows = rows.drop(mapping.headerRow + 1)
        val noAmount = { row: List<String> ->
            listOf(mapping.amountColumn, mapping.debitColumn, mapping.creditColumn).filter { it >= 0 }
                .all { c -> (money(row, c) ?: 0.0) == 0.0 }
        }
        (dataRows.firstOrNull { row -> StatementWords.openingBalance.containsMatchIn(row.getOrElse(mapping.descriptionColumn) { "" }) }
            ?: dataRows.firstOrNull()?.takeIf { noAmount(it) })
            ?.let { row -> opening = opening ?: money(row, mapping.balanceColumn) }
        return StatementSummary(printedIn = printedIn, printedOut = printedOut, openingBalance = opening, closingBalance = closing)
    }

    // Type-column values. Strong words decide on their own; weak ones only when nothing else matches.
    private val strongOut = setOf("withdrawal", "withdraw", "payment", "purchase", "expense", "expenses", "fee", "charge", "sent", "spend", "spending", "paid", "outgoing", "debited")
    private val strongIn = setOf("deposit", "income", "salary", "refund", "received", "receive", "incoming", "credited", "reversal", "interest")
    private val weakOut = setOf("out", "debit", "dr", "d")
    private val weakIn = setOf("in", "credit", "cr", "c")

    /** Direction from a type cell such as "DR", "Money Out", "Income", "Card purchase". */
    fun directionFromType(value: String): Direction? {
        val words = value.lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }.toSet()
        if (words.isEmpty()) return null
        // Money coming back is money in, whatever it was for ("Refund of purchase").
        if (words.any { it in setOf("refund", "refunded", "reversal", "reversed", "cashback", "chargeback") }) return Direction.IN
        val so = words.any { it in strongOut }
        val si = words.any { it in strongIn }
        if (si && !so) return Direction.IN
        if (so && !si) return Direction.OUT
        val wo = words.any { it in weakOut }
        val wi = words.any { it in weakIn }
        if (wi && !wo) return Direction.IN
        if (wo && !wi) return Direction.OUT
        return null
    }

    /** Turns spreadsheet rows into transactions; rows that aren't transactions (totals, notes) are skipped. */
    fun apply(rows: List<List<String>>, mapping: ColumnMapping): List<ParsedTransaction> =
        rows.drop(mapping.headerRow + 1).mapNotNull { row ->
            fun cell(i: Int) = if (i < 0) "" else row.getOrElse(i) { "" }.trim()
            val date = DateParser.parse(cell(mapping.dateColumn), mapping.dateFormat) ?: return@mapNotNull null
            val description = cell(mapping.descriptionColumn)
            if (StatementWords.openingBalance.containsMatchIn(description) || StatementWords.closingBalance.containsMatchIn(description)) {
                return@mapNotNull null
            }
            val dot = mapping.dotThousands

            val (amount, direction) = when (mapping.amountMode) {
                AmountMode.DEBIT_CREDIT -> {
                    val debit = AmountParser.parse(cell(mapping.debitColumn), dot)?.takeIf { it != 0.0 }
                    val credit = AmountParser.parse(cell(mapping.creditColumn), dot)?.takeIf { it != 0.0 }
                    when {
                        debit != null -> kotlin.math.abs(debit) to Direction.OUT
                        credit != null -> kotlin.math.abs(credit) to Direction.IN
                        else -> return@mapNotNull null
                    }
                }
                AmountMode.SINGLE -> {
                    val value = AmountParser.parse(cell(mapping.amountColumn), dot)?.takeIf { it != 0.0 } ?: return@mapNotNull null
                    val typed = if (mapping.typeColumn >= 0) directionFromType(cell(mapping.typeColumn)) else null
                    val direction = when {
                        typed != null -> typed
                        mapping.expensesArePositive -> if (value > 0) Direction.OUT else Direction.IN
                        else -> if (value < 0) Direction.OUT else Direction.IN
                    }
                    kotlin.math.abs(value) to direction
                }
            }
            val balance = if (mapping.balanceColumn >= 0) AmountParser.parse(cell(mapping.balanceColumn), dot) else null
            ParsedTransaction(date, description, amount, direction, balance)
        }

    /** Header text for each column, or "Column A", "Column B", … */
    /** Header text per column, blank where the file has none (the screen then says "Column B"). */
    fun columnLabels(rows: List<List<String>>, headerRow: Int): List<String> {
        val width = rows.maxOfOrNull { it.size } ?: 0
        val header = rows.getOrNull(headerRow)
        return (0 until width).map { i ->
            header?.getOrNull(i)?.trim().orEmpty()
        }
    }

    fun columnLetter(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return sb.toString()
    }
}
