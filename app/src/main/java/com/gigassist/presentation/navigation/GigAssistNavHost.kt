package com.gigassist.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gigassist.domain.usecase.GetDriverSettingsUseCase
import com.gigassist.presentation.dashboard.DashboardScreen
import com.gigassist.presentation.expenses.ExpensesScreen
import com.gigassist.presentation.onboarding.OnboardingScreen
import com.gigassist.presentation.settings.SettingsScreen

object GigAssistRoutes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val EXPENSES = "expenses"
}

@Composable
fun GigAssistNavHost(
    getDriverSettings: GetDriverSettingsUseCase
) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val settings = getDriverSettings()
        startDestination = if (settings != null) {
            GigAssistRoutes.DASHBOARD
        } else {
            GigAssistRoutes.ONBOARDING
        }
    }

    val destination = startDestination ?: return

    NavHost(
        navController = navController,
        startDestination = destination
    ) {
        composable(GigAssistRoutes.ONBOARDING) {
            OnboardingScreen(
                onNavigateToDashboard = {
                    navController.navigate(GigAssistRoutes.DASHBOARD) {
                        popUpTo(GigAssistRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(GigAssistRoutes.DASHBOARD) {
            DashboardScreen(
                onNavigateToSettings = {
                    navController.navigate(GigAssistRoutes.SETTINGS)
                },
                onNavigateToExpenses = {
                    navController.navigate(GigAssistRoutes.EXPENSES)
                }
            )
        }

        composable(GigAssistRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(GigAssistRoutes.EXPENSES) {
            ExpensesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
