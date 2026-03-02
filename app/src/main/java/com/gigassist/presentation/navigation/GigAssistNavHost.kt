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
import com.gigassist.presentation.onboarding.OnboardingScreen

object GigAssistRoutes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
}

@Composable
fun GigAssistNavHost(
    getDriverSettings: GetDriverSettingsUseCase
) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Decide destino inicial según si hay settings guardados
    LaunchedEffect(Unit) {
        val settings = getDriverSettings()
        startDestination = if (settings != null) {
            GigAssistRoutes.DASHBOARD
        } else {
            GigAssistRoutes.ONBOARDING
        }
    }

    // Mostrar nada hasta que se determine el destino
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
            DashboardScreen()
        }
    }
}
