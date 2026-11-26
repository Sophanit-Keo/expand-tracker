package com.expensetracker.domain

import com.expensetracker.data.BankStatement
import com.expensetracker.data.Expense
import kotlin.math.abs

/**
 * How an imported bank statement compares with what the app's cash flow shows for the same dates.
 *
 * The cash flow counts every entry in the period. It matches the statement when the entries
 * imported from it are all still there and nothing else (entries added by hand, sample data,
 * other imports) falls in the same dates.
 */
data class StatementStatus(
    val statement: BankStatement,
    /** What the cash flow shows for the statement's dates. */
    val appIn: Double,
    val appOut: Double,
    /** Entries in those dates that came from this statement. */
    val linkedIn: Double,
    val linkedOut: Double,
    /** Entries in those dates that didn't come from this statement. */
    val otherCount: Int,
    val otherIn: Double,
    val otherOut: Double,
    val sampleCount: Int,
) {
    val matches: Boolean get() = abs(appIn - statement.totalIn) < 0.01 && abs(appOut - statement.totalOut) < 0.01

    /** Statement money that isn't in the app (rows left unticked, or deleted later). */
    val missingIn: Double get() = (statement.totalIn - linkedIn).coerceAtLeast(0.0)
    val missingOut: Double get() = (statement.totalOut - linkedOut).coerceAtLeast(0.0)

    val appNet: Double get() = appIn - appOut
    val statementNet: Double get() = statement.totalIn - statement.totalOut
}

object StatementCheck {
    fun status(statement: BankStatement, entries: List<Expense>): StatementStatus {
        val inPeriod = Analytics.inRange(entries, statement.periodStart, statement.periodEnd)
        val (linked, other) = inPeriod.partition { it.statementId == statement.id }
        return StatementStatus(
            statement = statement,
            appIn = Analytics.total(Analytics.income(inPeriod)),
            appOut = Analytics.total(Analytics.spending(inPeriod)),
            linkedIn = Analytics.total(Analytics.income(linked)),
            linkedOut = Analytics.total(Analytics.spending(linked)),
            otherCount = other.size,
            otherIn = Analytics.total(Analytics.income(other)),
            otherOut = Analytics.total(Analytics.spending(other)),
            sampleCount = other.count { it.isSample },
        )
    }

    /** Statements whose dates overlap [period], newest first. */
    fun forPeriod(statements: List<BankStatement>, entries: List<Expense>, period: Period): List<StatementStatus> =
        statements
            .filter { !it.periodEnd.isBefore(period.start) && !it.periodStart.isAfter(period.end) }
            .map { status(it, entries) }

    /** The most recent known bank balance: the latest statement's closing balance. */
    fun latestBalance(statements: List<BankStatement>): BankStatement? =
        statements.filter { it.closingBalance != null }.maxByOrNull { it.periodEnd }
}
