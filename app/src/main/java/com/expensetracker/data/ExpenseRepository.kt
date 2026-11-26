package com.expensetracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {
    val expenses: Flow<List<Expense>> = dao.observeAll()

    suspend fun getAll(): List<Expense> = dao.getAll()

    suspend fun get(id: Long): Expense? = dao.getById(id)

    suspend fun save(expense: Expense) = dao.upsert(expense)

    suspend fun delete(expense: Expense) = dao.delete(expense)

    suspend fun addAll(expenses: List<Expense>) = dao.insertAll(expenses)

    suspend fun clear() = dao.deleteAll()
}
