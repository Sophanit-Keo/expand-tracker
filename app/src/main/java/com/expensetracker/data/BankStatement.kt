package com.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A bank statement that was imported: its period, what it contained, and the account balance at
 * the start and end. Entries imported from it point back here (Expense.statementId), so the cash
 * flow can be checked against the statement later.
 */
@Entity(tableName = "statements")
data class BankStatement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val importedAt: Long = System.currentTimeMillis(),
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** Totals of every transaction in the file (as reviewed), imported or not. */
    val totalIn: Double,
    val totalOut: Double,
    val transactionCount: Int,
    val openingBalance: Double?,
    val closingBalance: Double?,
)
