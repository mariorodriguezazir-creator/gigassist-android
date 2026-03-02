package com.gigassist.core.parser

import org.junit.Test
import org.junit.Assert.*

class MockParserTest {

    @Test
    fun debugUberParser() {
        val fullText = "Inicio DOP 98 +DOP 16.38 por inicio de viaje A 4 min (1.1 km) Almacenes El Encanto Viaje: 8 min (1.7 km) 154 Av. San Vicente de Paúl X Aceptar"
        
        val isOffer = fullText.contains("Viaje:", ignoreCase = true) &&
                      fullText.contains("Aceptar", ignoreCase = true) &&
                      fullText.contains("km", ignoreCase = true)
        
        println("isRealUberOffer: ${isOffer}")

        val fareRegex = Regex("""(?<!\+)DOP\s*(\d+(?:[.,]\d{1,2})?)""")
        val fareMatch = fareRegex.find(fullText)
        println("fareMatch: ${fareMatch?.value} -> group 1: ${fareMatch?.groupValues?.get(1)}")

        val distRegex = Regex("""[Vv]iaje:.*?(\d+(?:[.,]\d)?)\s*km""", RegexOption.IGNORE_CASE)
        val distMatch = distRegex.find(fullText)
        println("distMatch: ${distMatch?.value} -> group 1: ${distMatch?.groupValues?.get(1)}")

        val durRegex = Regex("""[Vv]iaje:\s*(\d+)\s*min""", RegexOption.IGNORE_CASE)
        val durMatch = durRegex.find(fullText)
        println("durMatch: ${durMatch?.value} -> group 1: ${durMatch?.groupValues?.get(1)}")

        assertTrue(isOffer)
        assertNotNull(fareMatch)
        assertNotNull(distMatch)
        assertNotNull(durMatch)
    }
}
