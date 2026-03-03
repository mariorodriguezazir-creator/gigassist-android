package com.gigassist.core.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gigassist.core.calculator.TripEvaluator
import com.gigassist.core.parser.DiDiTripOfferParser
import com.gigassist.domain.model.DistanceUnit
import com.gigassist.domain.model.DriverSettings
import com.gigassist.domain.model.TripEvaluationUiModel
import com.gigassist.domain.model.TripEvaluatorInput
import com.gigassist.domain.model.TripOfferRawData
import com.gigassist.domain.model.TripRecord
import com.gigassist.domain.repository.SettingsRepository
import com.gigassist.domain.repository.ShiftRepository
import com.gigassist.domain.repository.TripRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * NotificationListenerService para capturar ofertas de DiDi.
 *
 * DiDi bloquea el árbol de accesibilidad en su popup de viaje
 * en la versión latinoamericana. La solución confirmada es leer
 * la NOTIFICACIÓN del sistema que DiDi emite ANTES de mostrar
 * el popup, la cual SÍ contiene el texto del viaje.
 */
@AndroidEntryPoint
class DiDiNotificationListener : NotificationListenerService() {

    @Inject lateinit var tripEvaluator: TripEvaluator
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var shiftRepository: ShiftRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val didiParser = DiDiTripOfferParser()

    private val _evaluationFlow = MutableSharedFlow<TripEvaluationUiModel>(replay = 1)
    val evaluationFlow: SharedFlow<TripEvaluationUiModel> = _evaluationFlow.asSharedFlow()

    companion object {
        private const val DIDI_PACKAGE = "com.didiglobal.driver"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName != DIDI_PACKAGE) return

        val extras = notification.notification.extras ?: return
        val title   = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val full = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        Log.d("DiDiNotif", "RAW notif: $full")

        if (full.isBlank()) return

        val offer = didiParser.parse(full) ?: return

        Log.d("GigParser", "✅ DiDi OFERTA: fare=${offer.fare}, dist=${offer.distanceKm}, dur=${offer.durationMin}")
        Timber.d("DiDi offer from notification: fare=${offer.fare}, dist=${offer.distanceKm}, dur=${offer.durationMin}")

        serviceScope.launch {
            processTrip(offer)
        }
    }

    private suspend fun processTrip(rawData: TripOfferRawData) {
        try {
            val settings = settingsRepository.getSettings() ?: DriverSettings()

            val input = TripEvaluatorInput(
                fare = rawData.fare,
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                settings = settings,
                airportConfig = settings.airportConfig
            )
            val rates = tripEvaluator.evaluate(input)

            val activeShift = shiftRepository.getActiveShift()
            val shiftId = activeShift?.id ?: 0L

            val record = TripRecord(
                shiftId = shiftId,
                fare = rawData.fare,
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                platform = "DiDi",
                evaluationResult = rates.evaluationResult
            )
            tripRepository.saveTripRecord(record)
            Timber.d("DiDi trip saved: ${rates.evaluationResult} - ${rates.ratePerHour}/h")

            val uiModel = TripEvaluationUiModel(
                fare = rawData.fare,
                ratePerHour = rates.ratePerHour,
                ratePerDistanceUnit = rates.ratePerDistanceUnit,
                estimatedNetFare = rates.estimatedNetFare,
                evaluationResult = rates.evaluationResult,
                platform = "DiDi",
                distanceKm = rawData.distanceKm,
                durationMin = rawData.durationMin,
                currencySymbol = settings.countryCode.currencySymbol,
                distanceUnitLabel = if (settings.distanceUnit == DistanceUnit.KM) "km" else "mi"
            )
            _evaluationFlow.emit(uiModel)
        } catch (e: Exception) {
            Timber.e(e, "Error processing DiDi trip from notification")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.d("DiDiNotificationListener destroyed")
    }
}
