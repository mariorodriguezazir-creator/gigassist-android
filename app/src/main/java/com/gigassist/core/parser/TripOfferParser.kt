package com.gigassist.core.parser

import android.view.accessibility.AccessibilityNodeInfo
import com.gigassist.domain.model.TripOfferRawData

/**
 * Interfaz para parsear ofertas de viaje desde el árbol de accesibilidad.
 * Cada plataforma (Uber, DiDi) tiene su propia implementación
 * que extrae tarifa, distancia y duración usando regex sobre
 * los textos de los nodos hoja.
 *
 * Regla: NUNCA usar viewIdResourceName como único criterio.
 * SIEMPRE parsear por patrones de texto (regex).
 */
interface TripOfferParser {

    /**
     * Parsea el nodo raíz del árbol de accesibilidad y extrae
     * los datos de la oferta de viaje.
     *
     * @param rootNode nodo raíz del árbol de accesibilidad activo.
     * @return TripOfferRawData si se detectó una oferta válida, null en caso contrario.
     */
    fun parse(rootNode: AccessibilityNodeInfo): TripOfferRawData?
}
