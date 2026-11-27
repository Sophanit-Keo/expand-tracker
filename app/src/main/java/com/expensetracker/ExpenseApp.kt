package com.expensetracker

import android.app.Application
import com.expensetracker.data.AppDatabase
import com.expensetracker.data.ExpenseRepository
import com.expensetracker.data.OnlineRateSource
import com.expensetracker.data.RateSource
import com.expensetracker.data.RatesUpdater
import com.expensetracker.data.SettingsRepository
import com.expensetracker.importer.StatementImporter

/** Simple manual dependency container, created once per process. */
class AppContainer(app: Application) {
    private val database by lazy { AppDatabase.build(app) }
    val expenseRepository by lazy { ExpenseRepository(database.expenseDao(), database.categoryRuleDao(), database.statementDao()) }
    val statementImporter by lazy { StatementImporter(app) }
    val settingsRepository by lazy { SettingsRepository(app) }

    /** Where exchange rates are downloaded from; tests swap in a fake. */
    var rateSource: RateSource = OnlineRateSource()
    val ratesUpdater by lazy { RatesUpdater(settingsRepository, { rateSource }) }
}

class ExpenseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
