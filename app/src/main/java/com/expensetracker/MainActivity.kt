package com.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.UserSettings
import com.expensetracker.ui.ExpenseTrackerApp
import com.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = (application as ExpenseApp).container.settingsRepository.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
            ExpenseTrackerTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                ExpenseTrackerApp()
            }
        }
    }
}
