package com.expensetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One money movement: an expense (money out) or, when [type] is [EntryType.INCOME], money in.
 * The table keeps its original name so existing data needs no copying.
 */
const val SAMPLE_PREFIX = "sample|"

@Entity(tableName = "expenses", indices = [Index("date")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: Category,
    val note: String = "",
    val date: LocalDate,
    val createdAt: Long = System.currentTimeMillis(),
    /** Set for expenses imported from a bank statement; used to skip duplicates on re-import. */
    val importKey: String? = null,
    @ColumnInfo(defaultValue = "EXPENSE")
    val type: EntryType = category.type,
    /** The bank statement this entry was imported from, if any. */
    val statementId: Long? = null,
    /**
     * Set when the money moved in another currency: what was paid in [originalCurrency]. [amount] is
     * always in the main currency (converted at the rate given when the entry was saved), so totals,
     * charts and cash flow simply add it up.
     */
    val originalAmount: Double? = null,
    val originalCurrency: AppCurrency? = null,
) {
    /** The entry's own currency when it isn't the main one. */
    val foreign: Pair<Double, AppCurrency>? get() = if (originalAmount != null && originalCurrency != null) originalAmount to originalCurrency else null

    /** Main-currency units per one unit of [originalCurrency], as used for this entry. */
    val exchangeRate: Double? get() = originalAmount?.takeIf { it > 0 }?.let { amount / it }

    val isSample: Boolean get() = importKey?.startsWith(SAMPLE_PREFIX) == true

    val isIncome: Boolean get() = type == EntryType.INCOME

    /** The text shown as the main line of a transaction row. */
    val title: String get() = note.ifBlank { category.label }
}
