# PRD.md — GigAssist v1.0
## Plataforma: Android Nativo (Kotlin)
## Fecha: Febrero 2026
## Autor: Mario Estrella

---

## 1. RESUMEN EJECUTIVO

GigAssist es una app Android para conductores de Uber y DiDi
en países hispanohablantes. Usa AccessibilityService para leer
ofertas de viaje y muestra un overlay flotante con métricas de
rentabilidad (ganancia por hora, por km, ganancia neta),
coloreadas según umbrales configurados por el conductor.
Incluye dashboard de jornada, registro de gastos y analytics.

---

## 2. OBJETIVOS DE NEGOCIO

- Aumentar la ganancia neta por hora del conductor.
- Reducir aceptación de viajes poco rentables.
- Dar al conductor claridad sobre sus finanzas reales.

---

## 3. USUARIOS

- Conductor activo de Uber y/o DiDi en países hispanohablantes.
- Tiene Android y acceso a su teléfono mientras trabaja.
- No es técnico; la app debe ser simple e intuitiva.

---

## 4. FEATURES DEL MVP

### 4.1 AccessibilityService (TripScannerService)

Eventos a escuchar:
- TYPE_WINDOW_STATE_CHANGED
- TYPE_WINDOW_CONTENT_CHANGED

Paquetes a monitorear:
- com.ubercab.driver (Uber Driver)
- com.xiaoju.globalapp (DiDi Driver)

Datos a extraer (parsing por regex):
- Tarifa:    [RD$|$|COP|MXN|ARS...]\s?\d+[\.,]?\d*
- Distancia: \d+[\.,]?\d*\s?(km|mi|km\.|millas)
- Duración:  \d+\s?(min|mins|minutos|h|hrs)

Flujo:
1. Servicio detecta pantalla de oferta.
2. Extrae texto bruto del árbol de accesibilidad.
3. Pasa texto a TripOfferParser (por plataforma).
4. Parser emite TripOfferRawData.
5. TripEvaluator calcula TripRates.
6. OverlayController muestra TripEvaluationUiModel.

Privacidad:
- NO guardar nombres de pasajeros.
- NO guardar coordenadas exactas.
- Solo guardar: tarifa, distancia, duración, resultado de evaluación.

---

### 4.2 Motor de cálculo (TripEvaluator)

Inputs:
- fare: Double (moneda local)
- distanceKm: Double (siempre en km internamente)
- durationMin: Int
- operatingCostPerHour: Double
- thresholds: DriverThresholds (min/h, min/km)
- airportMode: AirportConfig? (opcional)

Outputs (TripRates):
- ratePerHour: Double
- ratePerDistanceUnit: Double
- estimatedNetFare: Double
- evaluationResult: GOOD | FAIR | POOR

Fórmulas:
- ratePerHour = (fare / durationMin) × 60
- ratePerKm   = fare / distanceKm
- netFare      = fare - (operatingCostPerHour × durationMin / 60)

Modo aeropuerto:
- durationTotal = durationMin + waitMinutes + boardingMin
                + (if returnEmpty → estimatedReturnMin else 0)
- ratePerHour   = (fare / durationTotal) × 60

EvaluationResult:
- GOOD → ratePerHour >= minPerHour AND ratePerKm >= minPerKm
- FAIR → solo uno de los dos cumple
- POOR → ninguno cumple

---

### 4.3 Overlay flotante (TripOverlayController)

Componente: ComposeView dentro de WindowManager

Datos mostrados:
- Tarifa total
- Ganancia/hora
- Ganancia/km
- Duración estimada
- Distancia total
- Estado: BUENO / REGULAR / POBRE

Permiso requerido: SYSTEM_ALERT_WINDOW

Comportamiento:
- Aparece automáticamente al detectar oferta.
- Se cierra automáticamente si la oferta desaparece
  o pasan más de 20 segundos (configurable).
- Botón manual de cerrar.
- No bloquea botones de aceptar/rechazar del conductor.

---

### 4.4 Configuración del conductor (DriverSettings)

Campos:
- country: CountryCode
- currency: CurrencyCode (DOP, COP, MXN, ARS, etc.)
- distanceUnit: DistanceUnit (KM — MILES en v2)
- minRatePerHour: Double
- minRatePerDistanceUnit: Double
- operatingCostPerHour: Double
- airportMode: AirportConfig

Pantalla de onboarding al primer uso.

---

### 4.5 Dashboard de turno (ShiftTracker)

Entidades:
- Shift (id, startTime, endTime, platform)
- TripRecord (id, shiftId, fare, distanceKm, durationMin,
              platform, evaluationResult, timestamp)
- Expense (id, shiftId?, category, amount, date, notes)

Métricas del dashboard:
- Total de viajes en el turno
- Ingresos brutos del turno
- Gastos del turno
- Ganancia neta del turno
- Horas activas
- Promedio de $/hora del turno

Vistas: diaria / semanal / mensual

---

### 4.6 Registro de gastos

Categorías: GASOLINA, MANTENIMIENTO, PEAJES, LAVADO, OTROS
Campos: categoría, monto, fecha, notas (opcional)

---

### 4.7 Analytics básicos

- Mejor día de la semana por ganancias
- Mejor horario por ganancias
- Comparativo Uber vs DiDi
- Top 5 días más rentables del mes

---

## 5. PANTALLAS

- Onboarding (solo primera vez)
- Home / Dashboard
- Configuración
- Gastos
- Analytics
- Overlay (flota sobre otras apps, no es una pantalla)

---

## 6. REQUISITOS NO FUNCIONALES

Rendimiento:
- Overlay visible en < 300 ms tras detectar oferta.
- Sin degradación tras 4 horas continuas de uso.

Batería:
- AccessibilityService activo solo cuando Uber/DiDi
  están en foreground.
- Sin wakelock permanente.

Privacidad:
- Sin datos personales de pasajeros.
- Sin envío de datos a servidores externos en v1.

Internacionalización:
- Idioma: Español (es) como único idioma en v1.
- Preparar res/values-en/ vacío para v2 (EE.UU., inglés).
- Moneda y unidad de distancia según DriverSettings,
  no por Locale del sistema.

---

## 7. PAÍSES SOPORTADOS (MVP)

| País              | Moneda | Código | Distancia |
|-------------------|--------|--------|-----------|
| Rep. Dominicana   | DOP    | DO     | km        |
| Colombia          | COP    | CO     | km        |
| México            | MXN    | MX     | km        |
| Argentina         | ARS    | AR     | km        |
| Perú              | PEN    | PE     | km        |
| Chile             | CLP    | CL     | km        |
| Venezuela         | VES    | VE     | km        |
| Ecuador           | USD    | EC     | km        |
| Bolivia           | BOB    | BO     | km        |
| Paraguay          | PYG    | PY     | km        |
| Uruguay           | UYU    | UY     | km        |
| España            | EUR    | ES     | km        |
| (v2) EE.UU.       | USD    | US     | millas    |

---

## 8. CRITERIOS DE ACEPTACIÓN DEL MVP

✅ TripScannerService detecta oferta en Uber RD (dispositivo real)
✅ TripScannerService detecta oferta en DiDi RD (dispositivo real)
✅ TripEvaluator calcula correctamente en tests unitarios
✅ Overlay aparece con datos correctos en dispositivo físico
✅ Configuración persiste tras cerrar y abrir la app
✅ Dashboard muestra viajes del turno actual
✅ App pasa 4 horas de prueba sin crash ni degradación

---

## 9. FASES DE DESARROLLO

Fase 1 — Core (AccessibilityService + Overlay + Evaluator)
Fase 2 — Configuración + Persistencia (Room + DriverSettings)
Fase 3 — Dashboard + ShiftTracker
Fase 4 — Gastos y Analytics básicos
Fase 5 — Modo aeropuerto + Soporte multi-país completo
Fase 6 — Pulido UX, pruebas finales, preparación Play Store
