package com.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

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
) {
    /** The text shown as the main line of a transaction row. */
    val title: String get() = note.ifBlank { category.label }
}
