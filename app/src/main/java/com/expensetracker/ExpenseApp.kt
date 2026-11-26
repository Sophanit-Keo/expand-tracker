package com.expensetracker

import android.app.Application
import com.expensetracker.data.AppDatabase
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.SettingsRepository
import com.expensetracker.importer.StatementImporter

/** Simple manual dependency container, created once per process. */
class AppContainer(app: Application) {
    private val database by lazy { AppDatabase.build(app) }
    val expenseRepository by lazy { ExpenseRepository(database.expenseDao(), database.categoryRuleDao()) }
    val statementImporter by lazy { StatementImporter(app) }
    val settingsRepository by lazy { SettingsRepository(app) }
}

class ExpenseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
