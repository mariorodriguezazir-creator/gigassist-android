fun main() {
    val fullText = """Inicio DOP 98 +DOP 16.38 por inicio de viaje A 4 min (1.1 km) Almacenes El Encanto Viaje: 8 min (1.7 km) 154 Av. San Vicente de Paúl X Aceptar"""
    val isOffer = fullText.contains("Viaje:", ignoreCase = true) &&
                  fullText.contains("Aceptar", ignoreCase = true) &&
                  fullText.contains("km", ignoreCase = true)
    println("isOffer: ${isOffer}")

    val fareRegex = Regex("""(?<!\+)DOP\s*(\d+(?:[.,]\d{1,2})?)""")
    val fareStr = fareRegex.find(fullText)?.groupValues?.get(1)

    val distRegex = Regex("""Viaje:.*?(\d+(?:[.,]\d)?)\s*km""", RegexOption.IGNORE_CASE)
    val distStr = distRegex.find(fullText)?.groupValues?.get(1)

    val durRegex = Regex("""Viaje:.*?(\d+)\s*min""", RegexOption.IGNORE_CASE)
    val durStr = durRegex.find(fullText)?.groupValues?.get(1)
    
    println("fare: ${fareStr}, dist: ${distStr}, dur: ${durStr}")
}
