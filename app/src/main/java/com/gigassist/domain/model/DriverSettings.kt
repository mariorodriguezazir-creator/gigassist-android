package com.gigassist.domain.model

/**
 * Configuración del conductor.
 * Se persiste en Room y se carga al iniciar la app.
 * Los umbrales definen cuándo un viaje es GOOD, FAIR o POOR.
 */
data class DriverSettings(
    val countryCode: CountryCode = CountryCode.DO,
    val minRatePerHour: Double = countryCode.defaultMinRatePerHour,
    val minRatePerDistanceUnit: Double = countryCode.defaultMinRatePerKm,
    val operatingCostPerHour: Double = 0.0,
    val distanceUnit: DistanceUnit = countryCode.distanceUnit,
    val airportConfig: AirportConfig? = null,
    val overlayAutoCloseSeconds: Int = 20
)
