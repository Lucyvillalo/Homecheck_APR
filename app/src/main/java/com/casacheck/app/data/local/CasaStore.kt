package com.casacheck.app.data.local

import android.content.Context
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaPhoto
import com.casacheck.app.domain.model.CasaRoom
import com.casacheck.app.domain.model.Condition
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object CasaStore {
    private const val PREFS = "casacheck_local"
    private const val KEY = "inspections"

    fun load(context: Context, account: String = "local"): List<CasaInspection> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(if (account == "local") KEY else "${KEY}_$account", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { inspectionFromJson(array.getJSONObject(it)) }
    }.getOrElse { throw IllegalStateException("No se pudieron leer las inspecciones locales. Se conserva el archivo original.", it) }

    @android.annotation.SuppressLint("ApplySharedPref") // Caller uses Dispatchers.IO and needs a confirmed durable write.
    fun save(context: Context, inspections: List<CasaInspection>, account: String = "local") {
        val array = JSONArray()
        inspections.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(if (account == "local") KEY else "${KEY}_$account", array.toString()).commit().also { check(it) { "No se pudo guardar la inspección en el dispositivo" } }
    }

    fun CasaInspection.toJson() = JSONObject().apply {
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
                                put("notes", item.notes); put("photoPath", item.photoPath); put("capturedAt", item.capturedAt); put("photoOrigin", item.photoOrigin)
                                put("extraPhotos", JSONArray().apply { item.extraPhotos.forEach { photo -> put(JSONObject().put("path", photo.path).put("capturedAt", photo.capturedAt).put("origin", photo.origin)) } })
                            })
                        }
                    })
                })
            }
        })
    }

    fun inspectionFromJson(json: JSONObject): CasaInspection {
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
                    capturedAt = itemJson.optLong("capturedAt"),
                    photoOrigin = itemJson.optString("photoOrigin", "CAMERA"),
                    extraPhotos = (itemJson.optJSONArray("extraPhotos") ?: JSONArray()).let { photos -> (0 until photos.length()).map { index -> photos.getJSONObject(index).let { CasaPhoto(it.getString("path"), it.optLong("capturedAt"), it.optString("origin", "CAMERA")) } } }
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
