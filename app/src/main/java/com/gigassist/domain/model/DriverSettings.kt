package com.gigassist.domain.model

/**
 * Configuración del conductor.
 * Se persiste en Room y se carga al iniciar la app.
 * Los umbrales definen cuándo un viaje es GOOD, FAIR o POOR.
 */
data class DriverSettings(
    val country: CountryCode = CountryCode.DO,
    val currencyCode: String = country.currencyCode,
    val distanceUnit: DistanceUnit = country.distanceUnit,
    val minRatePerHour: Double = country.defaultMinRatePerHour,
    val minRatePerDistanceUnit: Double = country.defaultMinRatePerKm,
    val operatingCostPerHour: Double = 0.0,
    val overlayAutoCloseSeconds: Int = 20
)
