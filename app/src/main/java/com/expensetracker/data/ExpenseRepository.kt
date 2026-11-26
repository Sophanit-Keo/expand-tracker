package com.expensetracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val dao: ExpenseDao,
    private val rules: CategoryRuleDao,
    private val statementDao: StatementDao,
) {
    val expenses: Flow<List<Expense>> = dao.observeAll()

    val statements: Flow<List<BankStatement>> = statementDao.observeAll()

    suspend fun getAll(): List<Expense> = dao.getAll()

    suspend fun get(id: Long): Expense? = dao.getById(id)

    suspend fun save(expense: Expense) = dao.upsert(expense)

    suspend fun delete(expense: Expense) = dao.delete(expense)

    suspend fun addAll(expenses: List<Expense>) = dao.insertAll(expenses)

    suspend fun clear() {
        dao.deleteAll()
        statementDao.deleteAll()
    }

    suspend fun removeSampleData(): Int = dao.deleteSamples()

    /** Removes every imported transaction and statement, e.g. to re-import with a fixed importer. */
    suspend fun removeImported(): Int {
        val removed = dao.deleteImported()
        statementDao.deleteAll()
        return removed
    }

    /**
     * Records an imported statement (replacing an earlier import of the same file and period) and
     * links the entries with [importKeys], whether imported now or before, to it.
     */
    suspend fun saveStatement(statement: BankStatement, importKeys: List<String>): Long {
        statementDao.findSame(statement.periodStart, statement.periodEnd, statement.fileName)
            .takeIf { it.isNotEmpty() }?.let { statementDao.delete(it) }
        val id = statementDao.insert(statement)
        importKeys.chunked(500).forEach { dao.linkToStatement(it, id) } // SQLite variable limit
        return id
    }

    suspend fun importKeys(): Set<String> = dao.importKeys().toSet()

    /** Categories the user picked for merchants in earlier imports, keyed by merchant. */
    suspend fun categoryRules(): Map<String, Category> = rules.getAll().associate { it.merchant to it.category }

    suspend fun saveCategoryRules(rules: Map<String, Category>) =
        this.rules.upsert(rules.map { (merchant, category) -> CategoryRule(merchant, category) })
}
