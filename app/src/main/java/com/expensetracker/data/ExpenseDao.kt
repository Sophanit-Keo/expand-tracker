package com.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Upsert
    suspend fun upsert(expense: Expense)

    @Insert
    suspend fun insertAll(expenses: List<Expense>)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT importKey FROM expenses WHERE importKey IS NOT NULL")
    suspend fun importKeys(): List<String>
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRule>

    @Upsert
    suspend fun upsert(rules: List<CategoryRule>)
}
