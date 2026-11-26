package com.expensetracker.importer

import kotlin.math.abs

/**
 * Uses the statement's running balance, the bank's own record, to confirm whether each row was
 * money in or out: if the balance dropped by the amount, money went out; if it rose, money came in.
 * Corrections are only applied when the balances are trustworthy, i.e. almost every checkable row
 * reconciles one way or the other. Otherwise the balance column is probably something else.
 */
object BalanceCheck {
    private const val TOLERANCE = 0.011

    fun apply(read: StatementRead): StatementRead {
        val txs = read.transactions
        if (txs.none { it.balance != null }) return read

        // Work oldest-first; statements are sometimes printed newest-first.
        val newestFirst = txs.size > 1 && txs.first().date > txs.last().date
        val order = if (newestFirst) txs.indices.reversed() else txs.indices
        val opening = read.summary.openingBalance

        // Pass 1: which way does each row reconcile?
        val implied = arrayOfNulls<Direction>(txs.size)
        var checkable = 0
        var reconciled = 0
        var previous = opening
        for (i in order) {
            val tx = txs[i]
            val balance = tx.balance
            if (balance != null && previous != null) {
                checkable++
                val out = abs(previous - tx.amount - balance) < TOLERANCE
                val inn = abs(previous + tx.amount - balance) < TOLERANCE
                if (out != inn) {
                    implied[i] = if (out) Direction.OUT else Direction.IN
                    reconciled++
                }
            }
            previous = balance ?: previous?.let { p ->
                when (implied[i] ?: tx.direction) {
                    Direction.OUT -> p - tx.amount
                    Direction.IN -> p + tx.amount
                    Direction.UNKNOWN -> null
                }
            }
        }
        val trustworthy = checkable > 0 && reconciled >= checkable * 0.8
        var fixed = 0
        val result = txs.mapIndexed { i, tx ->
            val better = implied[i]
            if (trustworthy && better != null && better != tx.direction) {
                fixed++
                tx.copy(direction = better)
            } else {
                tx
            }
        }

        // Opening and closing balances: printed ones win; otherwise work them out from the rows.
        val chronological = order.map { result[it] }
        val firstWithBalance = chronological.firstOrNull { it.balance != null && it.direction != Direction.UNKNOWN }
        val derivedOpening = firstWithBalance?.let { t ->
            val before = chronological.takeWhile { it !== t }
            if (before.any { it.direction == Direction.UNKNOWN }) null
            else before.fold(t.balance!! + if (t.direction == Direction.OUT) t.amount else -t.amount) { acc, b ->
                acc + if (b.direction == Direction.OUT) b.amount else -b.amount
            }
        }
        val derivedClosing = chronological.lastOrNull { it.balance != null }?.let { last ->
            val after = chronological.dropWhile { it !== last }.drop(1)
            if (after.any { it.direction == Direction.UNKNOWN }) null
            else after.fold(last.balance!!) { acc, t -> acc + if (t.direction == Direction.IN) t.amount else -t.amount }
        }
        return StatementRead(
            result,
            read.summary.copy(
                openingBalance = opening ?: derivedOpening,
                closingBalance = read.summary.closingBalance ?: derivedClosing,
                fixedByBalance = fixed,
                balanceBreaks = if (trustworthy) checkable - reconciled else 0,
            ),
        )
    }
}
