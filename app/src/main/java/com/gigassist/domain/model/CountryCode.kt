package com.gigassist.domain.model

/**
 * Países soportados por GigAssist.
 * Cada país define su moneda, símbolo, unidad de distancia y
 * umbrales por defecto sugeridos al conductor en el onboarding.
 *
 * Los defaultMinRate son valores orientativos que el conductor
 * puede modificar libremente en la pantalla de configuración.
 */
enum class CountryCode(
    val displayName: String,
    val currencyCode: String,
    val currencySymbol: String,
    val distanceUnit: DistanceUnit,
    val defaultMinRatePerHour: Double,
    val defaultMinRatePerKm: Double
) {
    DO("Rep. Dominicana", "DOP", "RD$", DistanceUnit.KM, 600.0, 35.0),
    CO("Colombia", "COP", "$", DistanceUnit.KM, 15000.0, 900.0),
    MX("México", "MXN", "$", DistanceUnit.KM, 120.0, 8.0),
    AR("Argentina", "ARS", "$", DistanceUnit.KM, 2000.0, 150.0),
    PE("Perú", "PEN", "S/", DistanceUnit.KM, 25.0, 1.5),
    CL("Chile", "CLP", "$", DistanceUnit.KM, 5000.0, 350.0),
    VE("Venezuela", "VES", "Bs.", DistanceUnit.KM, 10.0, 0.8),
    EC("Ecuador", "USD", "$", DistanceUnit.KM, 4.0, 0.3),
    BO("Bolivia", "BOB", "Bs.", DistanceUnit.KM, 30.0, 2.0),
    PY("Paraguay", "PYG", "₲", DistanceUnit.KM, 30000.0, 2000.0),
    UY("Uruguay", "UYU", "$", DistanceUnit.KM, 200.0, 15.0),
    ES("España", "EUR", "€", DistanceUnit.KM, 15.0, 1.0),
    // v2: EE.UU. — desactivado en onboarding hasta feature flag ENABLE_US_MARKET
    US("Estados Unidos", "USD", "$", DistanceUnit.MILES, 25.0, 2.0)
}
