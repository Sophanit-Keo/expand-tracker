package com.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    @Query("UPDATE expenses SET statementId = :statementId WHERE importKey IN (:keys)")
    suspend fun linkToStatement(keys: List<String>, statementId: Long)

    @Query("DELETE FROM expenses WHERE importKey LIKE 'sample|%'")
    suspend fun deleteSamples(): Int

    /** Everything that came from bank statements; entries typed in by hand stay. */
    @Query("DELETE FROM expenses WHERE importKey IS NOT NULL AND importKey NOT LIKE 'sample|%'")
    suspend fun deleteImported(): Int
}

@Dao
interface StatementDao {
    @Query("SELECT * FROM statements ORDER BY periodEnd DESC, importedAt DESC")
    fun observeAll(): Flow<List<BankStatement>>

    @Insert
    suspend fun insert(statement: BankStatement): Long

    @Query("SELECT id FROM statements WHERE periodStart = :start AND periodEnd = :end AND fileName = :fileName")
    suspend fun findSame(start: LocalDate, end: LocalDate, fileName: String): List<Long>

    @Query("DELETE FROM statements WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM statements")
    suspend fun deleteAll()
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRule>

    @Upsert
    suspend fun upsert(rules: List<CategoryRule>)
}
