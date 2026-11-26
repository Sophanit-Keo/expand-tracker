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
)

enum class StatementKind(val label: String) {
    CSV("CSV"),
    EXCEL("Excel"),
    PDF("PDF"),
}
