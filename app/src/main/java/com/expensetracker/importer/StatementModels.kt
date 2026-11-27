package com.expensetracker.importer

import java.time.LocalDate

/** Whether money left the account (an expense) or came in. */
enum class Direction { OUT, IN, UNKNOWN }

/** One transaction read from a statement, before the user reviews it. */
data class ParsedTransaction(
    val date: LocalDate,
    val description: String,
    /** Always positive; [direction] says which way the money went. */
    val amount: Double,
    val direction: Direction,
    /** The account balance printed after this transaction, when the statement has one. */
    val balance: Double? = null,
)

/**
 * What the statement says about itself, used to check that the import adds up: totals printed on
 * the statement, and the balance before the first and after the last transaction.
 */
data class StatementSummary(
    val printedIn: Double? = null,
    val printedOut: Double? = null,
    val openingBalance: Double? = null,
    val closingBalance: Double? = null,
    /** Rows whose money in/out was corrected using the running balance. */
    val fixedByBalance: Int = 0,
    /** Rows where the running balance doesn't follow from the amount (possible misread). */
    val balanceBreaks: Int = 0,
)

/** Transactions plus the statement's own figures. */
data class StatementRead(val transactions: List<ParsedTransaction>, val summary: StatementSummary)

enum class StatementKind(val label: String) {
    CSV("CSV"),
    EXCEL("Excel"),
    PDF("PDF"),
}

/** Phrases banks print next to balances and totals. */
internal object StatementWords {
    val openingBalance = Regex("""(?i)(opening|beginning|previous|starting|start)\s+balance|balance\s+(brought\s+)?(forward|b/?f)|brought\s+forward""")
    val closingBalance = Regex("""(?i)(closing|ending|end|final)\s+balance|balance\s+carried(\s+forward)?|balance\s+c/?f|carried\s+forward""")
    val totalIn = Regex("""(?i)total\s+(of\s+)?(money\s+in|credits?|deposits?|paid\s+in|cash\s+in|incoming|in)\b""")
    val totalOut = Regex("""(?i)total\s+(of\s+)?(money\s+out|debits?|withdrawals?|paid\s+out|cash\s+out|outgoing|out)\b""")
    val total = Regex("""(?i)^\s*(grand\s+)?(sub-?)?totals?\b""")
}

/** Why a file can't be imported. The import screen shows a message for each in the app's language. */
enum class ImportProblem {
    TOO_LARGE, CANT_OPEN, EXCEL_UNREADABLE, EXCEL_NO_SHEETS, EXCEL_EMPTY, EXCEL_TOO_LARGE, NOT_XLSX,
    OLD_EXCEL, NO_ROWS, PDF_DAMAGED, PDF_NO_TEXT,
}

class ImportException(val problem: ImportProblem) : java.io.IOException(problem.name)
