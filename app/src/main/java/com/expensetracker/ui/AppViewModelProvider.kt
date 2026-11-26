package com.expensetracker.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.expensetracker.ExpenseApp
import com.expensetracker.ui.analytics.AnalyticsViewModel
import com.expensetracker.ui.edit.EditExpenseViewModel
import com.expensetracker.ui.history.HistoryViewModel
import com.expensetracker.ui.home.HomeViewModel
import com.expensetracker.ui.importer.ImportViewModel
import com.expensetracker.ui.settings.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(container.expenseRepository, container.settingsRepository) }
        initializer { HistoryViewModel(container.expenseRepository, container.settingsRepository) }
        initializer { AnalyticsViewModel(container.expenseRepository, container.settingsRepository) }
        initializer { EditExpenseViewModel(createSavedStateHandle(), container.expenseRepository, container.settingsRepository) }
        initializer { SettingsViewModel(container.expenseRepository, container.settingsRepository) }
        initializer { ImportViewModel(container.statementImporter, container.expenseRepository, container.settingsRepository) }
    }
}

private val CreationExtras.container
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExpenseApp).container
