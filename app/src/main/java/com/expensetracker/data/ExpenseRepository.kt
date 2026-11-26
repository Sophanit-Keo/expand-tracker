package com.expensetracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao, private val rules: CategoryRuleDao) {
    val expenses: Flow<List<Expense>> = dao.observeAll()

    suspend fun getAll(): List<Expense> = dao.getAll()

    suspend fun get(id: Long): Expense? = dao.getById(id)

    suspend fun save(expense: Expense) = dao.upsert(expense)

    suspend fun delete(expense: Expense) = dao.delete(expense)

    suspend fun addAll(expenses: List<Expense>) = dao.insertAll(expenses)

    suspend fun clear() = dao.deleteAll()

    suspend fun importKeys(): Set<String> = dao.importKeys().toSet()

    /** Categories the user picked for merchants in earlier imports, keyed by merchant. */
    suspend fun categoryRules(): Map<String, Category> = rules.getAll().associate { it.merchant to it.category }

    suspend fun saveCategoryRules(rules: Map<String, Category>) =
        this.rules.upsert(rules.map { (merchant, category) -> CategoryRule(merchant, category) })
}
