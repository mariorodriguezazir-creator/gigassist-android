# SKILL: Configuración Multi-País (Hispanoamérica)

## Propósito
Manejar correctamente monedas y unidades de distancia
según el país del conductor, con soporte para v2 (EE.UU.).

---

## CountryCode (enum)

enum class CountryCode(
    val displayName: String,
    val currencyCode: String,
    val currencySymbol: String,
    val distanceUnit: DistanceUnit,
    val defaultMinRatePerHour: Double,
    val defaultMinRatePerKm: Double
) {
    DO("Rep. Dominicana", "DOP", "RD$",  KM, 600.0, 35.0),
    CO("Colombia",        "COP", "$",    KM, 15000.0, 900.0),
    MX("México",          "MXN", "$",    KM, 120.0, 8.0),
    AR("Argentina",       "ARS", "$",    KM, 2000.0, 150.0),
    PE("Perú",            "PEN", "S/",   KM, 25.0, 1.5),
    CL("Chile",           "CLP", "$",    KM, 5000.0, 350.0),
    VE("Venezuela",       "VES", "Bs.",  KM, 10.0, 0.8),
    EC("Ecuador",         "USD", "$",    KM, 4.0, 0.3),
    BO("Bolivia",         "BOB", "Bs.",  KM, 30.0, 2.0),
    PY("Paraguay",        "PYG", "₲",   KM, 30000.0, 2000.0),
    UY("Uruguay",         "UYU", "$",    KM, 200.0, 15.0),
    ES("España",          "EUR", "€",    KM, 15.0, 1.0),
    US("Estados Unidos",  "USD", "$",    MILES, 25.0, 2.0)  // v2
}

---

## DistanceConverter (singleton)

object DistanceConverter {
    fun kmToMiles(km: Double): Double = km * 0.621371
    fun milesToKm(miles: Double): Double = miles / 0.621371
    fun formatDistance(km: Double, unit: DistanceUnit): String =
        when (unit) {
            DistanceUnit.KM    -> "%.1f km".format(km)
            DistanceUnit.MILES -> "%.1f mi".format(kmToMiles(km))
        }
}

---

## CurrencyFormatter

object CurrencyFormatter {
    fun format(amount: Double, countryCode: CountryCode): String {
        val formatted = "%.2f".format(amount)
        return "${countryCode.currencySymbol} $formatted"
    }
}

---

## Selección en Onboarding

Flujo:
1. Mostrar lista de países (CountryCode.values() excepto US en v1)
2. Al seleccionar país:
   - Setear currencyCode y distanceUnit automáticamente
   - Pre-llenar umbrales con defaultMinRatePerHour
     y defaultMinRatePerKm como sugerencia
   - El conductor puede modificarlos antes de confirmar
3. Guardar en DriverSettings via Room

Pantalla de configuración:
- Permitir cambiar el país en cualquier momento
- Mostrar advertencia: "Cambiar el país actualizará tu moneda
  y unidad de distancia. Los umbrales no se modificarán
  automáticamente."

---

## Preparación para v2 (EE.UU. + Inglés)

- CountryCode.US ya está en el enum (desactivado en v1)
- Para activar en v2:
  - Habilitar US en la lista del onboarding
  - Crear res/values-en/strings.xml con traducciones
  - Activar feature flag: ENABLE_US_MARKET = true
- Toda la lógica de conversión ya soporta MILES
  a través de DistanceConverter
