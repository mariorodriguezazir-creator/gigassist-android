package com.gigassist.core.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.gigassist.domain.model.EvaluationResult

class TripBubbleOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun showBubble(fare: String, ratePerHour: String, evaluation: EvaluationResult) {
        handler.post {
            removeBubbleSync() // Remover existente si hay alguno

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                
                // Añadir esquinas redondeadas mediante un GradientDrawable
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(when (evaluation) {
                        EvaluationResult.GOOD -> Color.parseColor("#2E7D32") // Verde oscuro
                        EvaluationResult.FAIR -> Color.parseColor("#E65100") // Naranja oscuro
                        EvaluationResult.POOR -> Color.parseColor("#C62828") // Rojo oscuro
                    })
                }
                background = shape
                setPadding(48, 32, 48, 32)
                elevation = 8f
                
                // Añadir textos
                addView(TextView(context).apply {
                    text = "Ganancia estimada: $ratePerHour/h"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
                
                addView(TextView(context).apply {
                    val recomendacion = when(evaluation) {
                        EvaluationResult.GOOD -> "✅ ACEPTAR VIAJE"
                        EvaluationResult.FAIR -> "⚠️ REGULAR - TÚ DECIDES"
                        EvaluationResult.POOR -> "❌ RECHAZAR"
                    }
                    text = recomendacion
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, 8, 0, 0)
                })
                
                // Click para cerrarlo manualmente
                setOnClickListener {
                    removeBubble()
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 150 // Margen superior (para no tapar las notificaciones)
                windowAnimations = android.R.style.Animation_Toast // Animación sencilla
            }

            windowManager.addView(layout, params)
            overlayView = layout

            // Desaparecer automáticamente después de 12 segundos
            handler.postDelayed({
                removeBubble()
            }, 12000)
        }
    }

    fun removeBubble() {
        handler.post {
            removeBubbleSync()
        }
    }
    
    private fun removeBubbleSync() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
            overlayView = null
        }
    }
}
