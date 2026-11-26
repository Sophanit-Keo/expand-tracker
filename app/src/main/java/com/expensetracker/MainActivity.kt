package com.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.ui.AppStateViewModel
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.ExpenseTrackerApp
import com.expensetracker.ui.OnboardingScreen
import com.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    private val appState: AppStateViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep the splash up until settings are loaded, so the right theme shows from the first frame.
        splash.setKeepOnScreenCondition { appState.state.value == null }
        setContent {
            val state by appState.state.collectAsStateWithLifecycle()
            val current = state
            if (current != null) {
                ExpenseTrackerTheme(themeMode = current.settings.themeMode, dynamicColor = current.settings.dynamicColor) {
                    ExpenseTrackerApp()
                    AnimatedVisibility(
                        visible = current.showOnboarding,
                        enter = EnterTransition.None,
                        exit = fadeOut() + slideOutVertically { it / 4 },
                    ) {
                        OnboardingScreen(onFinish = appState::finishOnboarding)
                    }
                }
            }
        }
    }
}
