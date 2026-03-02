# SKILL: TripOverlayController — Overlay Flotante

## Propósito
Mostrar métricas de rentabilidad sobre cualquier app activa
(Uber/DiDi) sin bloquear la interacción del conductor.

---

## Clase principal

class TripOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: ComposeView? = null
    private val _evaluationState = MutableStateFlow<TripEvaluationUiModel?>(null)

    fun show(evaluation: TripEvaluationUiModel) {
        if (!Settings.canDrawOverlays(context)) {
            // Notificar al usuario que debe habilitar el permiso
            return
        }
        if (overlayView != null) {
            _evaluationState.value = evaluation
            return
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 16
        params.y = 100

        overlayView = ComposeView(context).apply {
            setContent {
                val evaluation by _evaluationState.collectAsState()
                evaluation?.let { TripEvaluationCard(it, onClose = { hide() }) }
            }
        }
        windowManager.addView(overlayView, params)
        _evaluationState.value = evaluation
    }

    fun hide() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        _evaluationState.value = null
    }
}

---

## Modelo de UI

data class TripEvaluationUiModel(
    val formattedFare: String,          // "RD$ 450"
    val formattedRatePerHour: String,   // "RD$ 900/h"
    val formattedRatePerKm: String,     // "RD$ 37.5/km"
    val formattedDuration: String,      // "18 min"
    val formattedDistance: String,      // "12 km"
    val evaluationResult: EvaluationResult
)

---

## Composable TripEvaluationCard

@Composable
fun TripEvaluationCard(
    model: TripEvaluationUiModel,
    onClose: () -> Unit
) {
    val backgroundColor = when (model.evaluationResult) {
        EvaluationResult.GOOD -> Color(0xFF2E7D32)   // Verde
        EvaluationResult.FAIR -> Color(0xFFF9A825)   // Amarillo
        EvaluationResult.POOR -> Color(0xFFC62828)   // Rojo
    }
    Card(
        modifier = Modifier.width(220.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        // Row con label y valor para cada métrica
        // Botón X para cerrar en esquina superior derecha
    }
}

---

## Auto-cierre
- Usar LaunchedEffect o un Job en coroutines con delay()
- Tiempo configurable desde DriverSettings (default: 20 segundos)
- Si el AccessibilityService ya no detecta la pantalla de oferta,
  llamar a hide() inmediatamente.

---

## Permiso requerido
- android.permission.SYSTEM_ALERT_WINDOW
- Verificar con Settings.canDrawOverlays(context)
- Si no tiene permiso: mostrar snackbar en la app principal
  con botón que lleva a Settings especiales:
  Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
