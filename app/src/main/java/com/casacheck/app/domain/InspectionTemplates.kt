package com.casacheck.app.domain

import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaRoom

fun roomsFor(type: String): List<CasaRoom> {
    fun item(name: String, critical: Boolean = false) = CasaItem(name = name, critical = critical)
    val general = listOf(item("Vista general", true), item("Paredes", true), item("Techo", true),
        item("Piso", true), item("Puerta"), item("Ventanas"), item("Iluminación", true))
    val namesAndExtras = when (type) {
        "Salón", "Finca", "Quinta", "Cancha" -> listOf(
            "Área principal" to listOf(item("Mesas", true), item("Sillas", true), item("Equipo de sonido")),
            "Cocina / servicio" to listOf(item("Lavaplatos", true), item("Estufa"), item("Refrigerador")),
            "Baños" to listOf(item("Vista general del baño", true), item("Sanitario", true), item("Lavamanos", true), item("Ducha")),
            "Zonas comunes" to listOf(item("Mobiliario", true), item("Accesos"))
        )
        "Cuarto universitario" -> listOf(
            "Habitación principal" to listOf(item("Cama"), item("Clóset"), item("Aire acondicionado")),
            "Baño" to listOf(item("Vista general del baño", true), item("Sanitario", true), item("Lavamanos", true), item("Ducha")),
            "Zonas comunes" to listOf(item("Mobiliario"))
        )
        else -> listOf(
            "Habitación principal" to listOf(item("Cama"), item("Clóset"), item("Aire acondicionado")),
            "Cocina" to listOf(item("Lavaplatos", true), item("Estufa"), item("Refrigerador")),
            "Baños" to listOf(item("Vista general del baño", true), item("Sanitario", true), item("Lavamanos", true), item("Ducha")),
            "Sala / comedor" to listOf(item("Mobiliario")),
            "Zonas comunes" to listOf(item("Accesos"))
        )
    }
    return namesAndExtras.map { (name, extras) -> CasaRoom(name = name, items = general + extras) }
}
