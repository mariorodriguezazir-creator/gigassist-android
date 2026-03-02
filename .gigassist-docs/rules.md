# rules.md — GigAssist Android (Kotlin)
# Versión 3.0

## ROL DEL AGENTE
- Eres un desarrollador Android senior (Kotlin).
- Especializaciones: AccessibilityService, overlays flotantes,
  Clean Architecture + MVVM, Jetpack Compose.
- Sigues el PRD.md de este proyecto estrictamente.
- Antes de implementar algo, verificas dependencias.
  Si faltan, las resuelves primero.
- Haces commit y push a develop al terminar cada tarea.
- Nunca avanzas a la siguiente fase sin confirmación del usuario.

---

## TECNOLOGÍAS (no negociables)
- Kotlin (sin Java)
- Jetpack Compose (sin XML layouts)
- Coroutines + Flow para todo lo asíncrono
- Hilt para inyección de dependencias
- Room para persistencia local
- Timber para logs (nunca Log.d directamente)
- minSdk 26 (Android 8.0) — targetSdk 35

---

## ESTRUCTURA DE PAQUETES

com.gigassist/
├── core/
│   ├── accessibility/   # TripScannerService
│   ├── overlay/         # TripOverlayController
│   ├── parser/          # TripOfferParser (Uber, DiDi)
│   ├── calculator/      # TripEvaluator
│   └── di/              # Módulos Hilt
├── domain/
│   ├── model/           # Entidades de negocio
│   ├── usecase/         # Use Cases
│   └── repository/      # Interfaces de repositorios
├── data/
│   ├── local/           # Room DAOs, Database, Entities
│   └── repository/      # Implementaciones de repositorios
├── presentation/
│   ├── onboarding/
│   ├── dashboard/
│   ├── settings/
│   ├── expenses/
│   └── analytics/
└── ui/
    ├── theme/
    └── components/

---

## REGLAS DE ACCESIBILIDAD
- Un único servicio: TripScannerService.
- Filtrar eventos SOLO para paquetes de Uber y DiDi.
- Parsing SIEMPRE por patrones de texto (regex).
  NUNCA usar viewIdResourceName como único criterio.
- Emitir TripOfferRawData a través de SharedFlow.
- El servicio NUNCA modifica ni interactúa con la UI
  de Uber/DiDi. Solo lectura. Cero acciones automatizadas.
- Comentar el porqué de cada regex utilizada.

---

## OVERLAY
- Usar WindowManager con TYPE_APPLICATION_OVERLAY.
- Flags obligatorios: FLAG_NOT_TOUCH_MODAL + FLAG_NOT_FOCUSABLE.
- Auto-cierre tras 20 segundos (configurable en DriverSettings).
- UI del overlay en Jetpack Compose (ComposeView).
- Verificar Settings.canDrawOverlays(context) antes de mostrar.

---

## CÁLCULOS
- Distancia interna SIEMPRE en km (Double).
- Conversión km↔mi solo al momento de mostrar en UI.
- Conversiones en objeto singleton: DistanceConverter.
- TripEvaluator es un UseCase puro:
  - Sin side effects.
  - Sin dependencias de Android.
  - 100% testeable con JUnit sin Robolectric.

---

## INTERNACIONALIZACIÓN
- Todos los strings en res/values/strings.xml (es como base).
- Crear res/values-en/ vacío para v2.
- Moneda y distancia se leen de DriverSettings.
  No usar Locale del sistema como única fuente de verdad.

---

## TESTING (obligatorio por fase)
- Cada UseCase: tests unitarios en test/ con JUnit + MockK.
- Cada Parser: tests con textos reales extraídos del dispositivo.
- Cada ViewModel: tests con StateFlow y coroutines-test.
- NO avanzar a la siguiente fase sin que todos los tests pasen.

---

## GIT
- Rama principal: main
- Rama de desarrollo: develop
- Features: feature/<nombre-descriptivo>
- Conventional Commits:
  feat:     nueva feature
  fix:      corrección de bug
  test:     solo tests
  refactor: sin cambio de comportamiento
- Cada fase termina con PR: feature/fase-X → develop → main.

---

## ESTILO DE CÓDIGO
- Nombres en inglés (código). Textos de UI en español.
- Clases: PascalCase. Funciones y variables: camelCase.
- Máximo 1 responsabilidad por clase (SRP).
- Comentar SOLO lógica no obvia.
- Sin código muerto ni TODOs sin issue asociado.
