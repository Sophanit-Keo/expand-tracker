package com.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** "Transactions from this merchant belong in this category", learned from your imports. */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val merchant: String,
    val category: Category,
)
