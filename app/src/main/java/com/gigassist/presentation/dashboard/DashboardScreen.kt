package com.gigassist.presentation.dashboard

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gigassist.core.capture.ScreenCaptureService
import com.gigassist.domain.model.EvaluationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToExpenses: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var captureActive by remember { mutableStateOf(false) }

    // Launcher para MediaProjection
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("proj_data", result.data)
                putExtra("proj_code", result.resultCode)
            }
            ContextCompat.startForegroundService(context, svc)
            captureActive = true
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GigAssist", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToExpenses,
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Gastos")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Captura de Pantalla ──
                CaptureCard(
                    captureActive = captureActive,
                    onStartCapture = {
                        val mgr = context.getSystemService(
                            Activity.MEDIA_PROJECTION_SERVICE
                        ) as MediaProjectionManager
                        projectionLauncher.launch(mgr.createScreenCaptureIntent())
                    },
                    onStopCapture = {
                        context.stopService(Intent(context, ScreenCaptureService::class.java))
                        captureActive = false
                    },
                    onOpenNotificationSettings = {
                        context.startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        )
                    }
                )

                // ── Shift Card ──
                ShiftCard(
                    state = state,
                    onStartShift = viewModel::onStartShift,
                    onEndShift = viewModel::onEndShift
                )

                // ── Last Trip Card ──
                state.lastTripEvaluation?.let { trip ->
                    LastTripCard(trip = trip)
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(
    captureActive: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (captureActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (captureActive) "📸 Monitoreo activo" else "📸 Monitoreo de ofertas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (captureActive)
                    "Capturando pantalla — ofertas de Uber y DiDi se detectarán automáticamente"
                else
                    "Activa la captura de pantalla para detectar ofertas de viaje",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!captureActive) {
                Button(
                    onClick = onStartCapture,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("📸 Iniciar captura de pantalla")
                }
            } else {
                OutlinedButton(
                    onClick = onStopCapture,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("⏹ Detener captura")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text("🔔 Notificaciones (complemento)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ShiftCard(
    state: DashboardUiState,
    onStartShift: (String) -> Unit,
    onEndShift: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (!state.hasActiveShift) {
                Text(
                    text = "Sin turno activo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Inicia un turno para rastrear viajes y ganancias",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onStartShift("Uber") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Uber") }
                    OutlinedButton(
                        onClick = { onStartShift("DiDi") },
                        modifier = Modifier.weight(1f)
                    ) { Text("DiDi") }
                }
            } else {
                Text(
                    text = "Turno Activo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                val stats = state.shiftStats
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem(
                        label = "Viajes",
                        value = "${stats.totalTrips}",
                        modifier = Modifier.weight(1f)
                    )
                    StatItem(
                        label = "Bruto",
                        value = "${state.currencySymbol}${String.format("%.0f", stats.grossEarnings)}",
                        modifier = Modifier.weight(1f)
                    )
                    StatItem(
                        label = "Neto",
                        value = "${state.currencySymbol}${String.format("%.0f", stats.netEarnings)}",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem(
                        label = "Horas",
                        value = String.format("%.1f", stats.activeHours),
                        modifier = Modifier.weight(1f)
                    )
                    StatItem(
                        label = "${state.currencySymbol}/h Prom.",
                        value = String.format("%.0f", stats.averageRatePerHour),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onEndShift,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Finalizar turno") }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LastTripCard(trip: com.gigassist.domain.model.TripEvaluationUiModel) {
    val evalColor = when (trip.evaluationResult) {
        EvaluationResult.GOOD -> Color(0xFF4CAF50)
        EvaluationResult.FAIR -> Color(0xFFFFC107)
        EvaluationResult.POOR -> Color(0xFFF44336)
    }
    val evalLabel = when (trip.evaluationResult) {
        EvaluationResult.GOOD -> "BUENO"
        EvaluationResult.FAIR -> "REGULAR"
        EvaluationResult.POOR -> "POBRE"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Último viaje",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = evalLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = evalColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = "Tarifa",
                    value = "${trip.currencySymbol}${String.format("%.0f", trip.fare)}",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "${trip.currencySymbol}/h",
                    value = String.format("%.0f", trip.ratePerHour),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "${trip.currencySymbol}/${trip.distanceUnitLabel}",
                    value = String.format("%.1f", trip.ratePerDistanceUnit),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
