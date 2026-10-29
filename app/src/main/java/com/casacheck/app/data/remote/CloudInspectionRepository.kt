package com.casacheck.app.data.remote

import com.casacheck.app.data.local.CasaStore
import com.casacheck.app.domain.model.CasaInspection
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONObject

/** Manual data backup only. Evidence files and signatures never leave the device. */
class CloudInspectionRepository(private val uid: String) {
    private val database = FirebaseFirestore.getInstance()
    private val documents get() = database.collection("usuarios").document(uid).collection("inspecciones")

    suspend fun upload(inspections: List<CasaInspection>) {
        for (inspection in inspections) {
            val portable = inspection.dataOnlySnapshot()
            val payload = with(CasaStore) { portable.toJson().toString() }
            check(payload.toByteArray().size < 900_000) { "La inspección excede el tamaño permitido." }
            val userRoot = database.collection("usuarios").document(uid)
            val records = mutableListOf<Triple<String, String, Map<String, Any>>>()
            records += Triple("propiedades", inspection.propertyId, mapOf("idPropiedad" to inspection.propertyId,
                "nombre" to inspection.propertyName, "direccion" to inspection.address, "tipo" to inspection.type,
                "idCreador" to uid, "idUsuario" to uid))
            records += Triple("inventario", inspection.id, mapOf("idInventario" to inspection.id,
                "idInspeccion" to inspection.id, "idUsuario" to uid, "nombre" to inspection.propertyName))
            portable.rooms.forEach { room ->
                val spaceId = "${inspection.id}_${room.id}"
                records += Triple("espacios", spaceId, mapOf("idEspacio" to spaceId, "idInspeccion" to inspection.id,
                    "idUsuario" to uid, "nombre" to room.name, "tipo" to room.name))
                room.items.forEach { item ->
                    val checklistId = "${inspection.id}_${item.id}"
                    records += Triple("checklists", checklistId, mapOf("idChecklist" to checklistId,
                        "idEspacio" to spaceId, "idUsuario" to uid, "elemento" to item.name,
                        "estado" to item.condition.name, "observacion" to item.notes))
                    records += Triple("elementos", checklistId, mapOf("idElemento" to checklistId,
                        "idInventario" to inspection.id, "idUsuario" to uid, "nombre" to item.name,
                        "cantidad" to item.quantity, "estado" to item.condition.name, "observacion" to item.notes))

                }
            }
            for (chunk in records.chunked(450)) {
                val batch = database.batch()
                chunk.forEach { (collection, id, data) -> batch.set(userRoot.collection(collection).document(id), data) }
                batch.commit().awaitOnline()
            }
            // Publish the snapshot after structured data succeeds; no media upload is attempted.
            documents.document(inspection.id).set(mapOf("idUsuario" to uid, "idInspeccion" to inspection.id,
                "idPropiedad" to inspection.propertyId, "tipoInspeccion" to inspection.phase,
                "backupMode" to "DATA_ONLY", "schemaVersion" to 2,
                "completada" to portable.completed, "completadaEnOrigen" to inspection.completed, "fecha" to com.google.firebase.Timestamp(java.util.Date(inspection.createdAt)), "estado" to if (portable.completed) "Completada" else "Borrador", "payload" to payload,
                "actualizado" to FieldValue.serverTimestamp())).awaitOnline()
        }
    }

    suspend fun download(): List<CasaInspection> = documents.get(Source.SERVER).awaitOnline().documents.map { document ->
        val inspection = CasaStore.inspectionFromJson(JSONObject(document.getString("payload") ?: error("Registro sin datos")))
        check(inspection.id == document.id && inspection.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Identificador de inspección inválido" }
        check(inspection.rooms.all { room -> room.items.all { it.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) } }) { "Identificador de evidencia inválido" }
        // A restored data record is a draft until photos and signatures are supplied again.
        inspection.dataOnlySnapshot()
    }
}
