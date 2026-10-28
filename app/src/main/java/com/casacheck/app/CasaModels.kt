package com.casacheck.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Condition(val label: String) {
    PENDING("Sin revisar"), GOOD("Bueno"), REGULAR("Regular"),
    STAINED("Manchado"), DAMAGED("Dañado"), MISSING("Faltante")
}

data class CasaItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val critical: Boolean = false,
    val condition: Condition = Condition.PENDING,
    val quantity: Int = 1,
    val notes: String = "",
    val photoPath: String = "",
    val capturedAt: Long = 0L
)

data class CasaRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<CasaItem>
)

data class CasaInspection(
    val id: String = UUID.randomUUID().toString(),
    val propertyId: String = UUID.randomUUID().toString(),
    val propertyName: String,
    val address: String,
    val type: String,
    val phase: String = "ANTES",
    val baselineId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val rooms: List<CasaRoom>,
    val ownerName: String = "",
    val renterName: String = "",
    val ownerSignature: String = "",
    val renterSignature: String = "",
    val reportPath: String = ""
)

object CasaStore {
    private const val PREFS = "casacheck_local"
    private const val KEY = "inspections"

    fun load(context: Context): List<CasaInspection> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { inspectionFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(context: Context, inspections: List<CasaInspection>) {
        val array = JSONArray()
        inspections.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    private fun CasaInspection.toJson() = JSONObject().apply {
        put("id", id); put("propertyId", propertyId); put("propertyName", propertyName)
        put("address", address); put("type", type); put("phase", phase); put("baselineId", baselineId)
        put("createdAt", createdAt); put("completed", completed); put("ownerName", ownerName)
        put("renterName", renterName); put("ownerSignature", ownerSignature)
        put("renterSignature", renterSignature); put("reportPath", reportPath)
        put("rooms", JSONArray().apply {
            rooms.forEach { room ->
                put(JSONObject().apply {
                    put("id", room.id); put("name", room.name)
                    put("items", JSONArray().apply {
                        room.items.forEach { item ->
                            put(JSONObject().apply {
                                put("id", item.id); put("name", item.name); put("critical", item.critical)
                                put("condition", item.condition.name); put("quantity", item.quantity)
                                put("notes", item.notes); put("photoPath", item.photoPath); put("capturedAt", item.capturedAt)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun inspectionFromJson(json: JSONObject): CasaInspection {
        val roomsJson = json.optJSONArray("rooms") ?: JSONArray()
        val rooms = (0 until roomsJson.length()).map { ri ->
            val roomJson = roomsJson.getJSONObject(ri)
            val itemsJson = roomJson.optJSONArray("items") ?: JSONArray()
            val items = (0 until itemsJson.length()).map { ii ->
                val itemJson = itemsJson.getJSONObject(ii)
                CasaItem(
                    id = itemJson.optString("id", UUID.randomUUID().toString()),
                    name = itemJson.optString("name"),
                    critical = itemJson.optBoolean("critical"),
                    condition = runCatching { Condition.valueOf(itemJson.optString("condition")) }.getOrDefault(Condition.PENDING),
                    quantity = itemJson.optInt("quantity", 1),
                    notes = itemJson.optString("notes"),
                    photoPath = itemJson.optString("photoPath"),
                    capturedAt = itemJson.optLong("capturedAt")
                )
            }
            CasaRoom(id = roomJson.optString("id", UUID.randomUUID().toString()), name = roomJson.optString("name"), items = items)
        }
        return CasaInspection(
            id = json.optString("id", UUID.randomUUID().toString()),
            propertyId = json.optString("propertyId", UUID.randomUUID().toString()),
            propertyName = json.optString("propertyName"), address = json.optString("address"),
            type = json.optString("type"), phase = json.optString("phase", "ANTES"),
            baselineId = json.optString("baselineId"), createdAt = json.optLong("createdAt"),
            completed = json.optBoolean("completed"), rooms = rooms,
            ownerName = json.optString("ownerName"), renterName = json.optString("renterName"),
            ownerSignature = json.optString("ownerSignature"), renterSignature = json.optString("renterSignature"),
            reportPath = json.optString("reportPath")
        )
    }
}

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

fun newAfter(before: CasaInspection) = before.copy(
    id = UUID.randomUUID().toString(), phase = "DESPUÉS", baselineId = before.id,
    createdAt = System.currentTimeMillis(), completed = false, ownerName = "", renterName = "",
    ownerSignature = "", renterSignature = "", reportPath = "",
    rooms = before.rooms.map { room -> room.copy(items = room.items.map { item ->
        item.copy(condition = Condition.PENDING, quantity = 1, notes = "", photoPath = "", capturedAt = 0L)
    }) }
)

fun missingRequirements(inspection: CasaInspection): List<String> = inspection.rooms.flatMap { room ->
    room.items.filter { item ->
        item.condition == Condition.PENDING ||
        (item.critical && item.photoPath.isBlank()) ||
        (item.condition == Condition.MISSING && item.quantity != 0) ||
        (item.condition != Condition.GOOD && item.condition != Condition.PENDING && item.notes.isBlank())
    }.map { "${room.name}: ${it.name}" }
}
