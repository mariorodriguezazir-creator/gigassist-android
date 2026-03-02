package com.gigassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gigassist.domain.usecase.GetDriverSettingsUseCase
import com.gigassist.presentation.navigation.GigAssistNavHost
import com.gigassist.ui.theme.GigAssistTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var getDriverSettings: GetDriverSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GigAssistTheme {
                GigAssistNavHost(getDriverSettings = getDriverSettings)
            }
        }
    }
}