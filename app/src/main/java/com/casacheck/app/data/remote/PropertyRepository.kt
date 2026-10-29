package com.casacheck.app.data.remote

import com.casacheck.app.domain.account.AccountRole
import com.casacheck.app.data.local.CasaStore
import com.casacheck.app.domain.model.CasaInspection
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Public advertisements contain no private address or inspection evidence. */
data class PropertyRecord(val id: String, val ownerUid: String, val name: String, val address: String,
    val type: String, val tenantUid: String)
data class RentalListing(val id: String, val ownerUid: String, val title: String, val zone: String,
    val description: String, val priceCents: Long)
data class RentalApplication(val id: String, val propertyId: String, val applicantUid: String,
    val applicantName: String, val status: String, val inviteCode: String, val propertyName: String = "")

class PropertyRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val uid get() = requireNotNull(auth.currentUser).uid
    suspend fun role(allowCache: Boolean = false): String {
        val ref = db.collection("usuarios").document(uid)
        val profile = try { ref.get(Source.SERVER).awaitOnline() }
        catch (e: Exception) {
            val offline = e is kotlinx.coroutines.TimeoutCancellationException ||
                (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE)
            if (!allowCache || !offline) throw e
            ref.get(Source.CACHE).awaitOnline()
        }
        return AccountRole.fromStored(profile.getString("rol").orEmpty()).label
    }
    suspend fun properties(): List<PropertyRecord> {
        if (AccountRole.fromStored(role()) == AccountRole.STAFF)
            return StaffAssignmentRepository(auth,db).assignedProperties()
        return db.collection("properties")
        .whereArrayContains("memberUids", uid).get(Source.SERVER).awaitOnline().documents.map {
            PropertyRecord(it.id, it.getString("ownerUid").orEmpty(), it.getString("name").orEmpty(),
                it.getString("address").orEmpty(), it.getString("type").orEmpty(), it.getString("tenantUid").orEmpty())
        }
    }
    suspend fun create(name: String, address: String, type: String) {
        check(AccountRole.fromStored(role()).managesRentals) { "Solo un propietario puede registrar inmuebles." }
        require(name.trim().length in 3..100 && address.trim().length in 5..300) { "Escribe un nombre de 3 a 100 caracteres y una dirección de 5 a 300." }
        require(type in listOf("Casa", "Apartamento", "Salón"))
        val ref = db.collection("properties").document()
        ref.set(mapOf("ownerUid" to uid, "name" to name.trim(), "address" to address.trim(), "type" to type,
            "tenantUid" to "", "memberUids" to listOf(uid), "activeInviteId" to "")).awaitOnline()
    }
    suspend fun listings(): List<RentalListing> = db.collection("rentalListings").whereEqualTo("published", true)
        .limit(100).get(Source.SERVER).awaitOnline().documents.map {
            RentalListing(it.id, it.getString("ownerUid").orEmpty(), it.getString("title").orEmpty(),
                it.getString("zone").orEmpty(), it.getString("description").orEmpty(), it.getLong("priceCents") ?: 0)
        }
    suspend fun publish(p: PropertyRecord, zone: String, description: String, price: String) {
        check(AccountRole.fromStored(role()).managesRentals) { "Solo el propietario puede publicar anuncios." }
        require(p.ownerUid == uid)
        check(p.tenantUid.isBlank()) { "La propiedad ya tiene inquilino. No se puede anunciar como disponible." }
        val amount = price.trim().replace(',', '.').toBigDecimalOrNull()
        require(amount != null && amount.scale() <= 2 && amount > java.math.BigDecimal.ZERO && amount <= java.math.BigDecimal("1000000")) { "El precio debe ser positivo, con un máximo de dos decimales." }
        require(zone.trim().length in 3..100 && description.trim().length in 10..2000) { "Indica una zona de 3 a 100 caracteres y una descripción de 10 a 2000." }
        db.collection("rentalListings").document(p.id).set(mapOf("ownerUid" to uid, "title" to p.name,
            "zone" to zone.trim(), "description" to description.trim(), "priceCents" to amount.movePointRight(2).longValueExact(),
            "published" to true)).awaitOnline()
    }
    suspend fun withdraw(p: PropertyRecord) { db.collection("rentalListings").document(p.id).delete().awaitOnline() }
    suspend fun apply(listing: RentalListing) {
        check(AccountRole.fromStored(role()).requestsRentals) { "Las solicitudes de alquiler corresponden al perfil inquilino." }
        require(listing.ownerUid != uid) { "No puedes solicitar tu propio anuncio." }
        val ref = db.collection("rentalApplications").document("${listing.id}_$uid")
        db.runTransaction { tx ->
            check(!tx.get(ref).exists()) { "Ya enviaste una solicitud para este anuncio." }
            tx.set(ref, mapOf("propertyId" to listing.id, "ownerUid" to listing.ownerUid, "applicantUid" to uid,
                "applicantName" to auth.currentUser?.displayName.orEmpty().take(100), "propertyName" to listing.title, "status" to "pending", "inviteCode" to ""))
        }.awaitOnline()
    }
    suspend fun applications(owner: Boolean): List<RentalApplication> = db.collection("rentalApplications")
        .whereEqualTo(if (owner) "ownerUid" else "applicantUid", uid).get(Source.SERVER).awaitOnline().documents.map {
            RentalApplication(it.id, it.getString("propertyId").orEmpty(), it.getString("applicantUid").orEmpty(),
                it.getString("applicantName").orEmpty(), it.getString("status").orEmpty(), it.getString("inviteCode").orEmpty(), it.getString("propertyName").orEmpty())
        }
    suspend fun decline(a: RentalApplication) { db.collection("rentalApplications").document(a.id).update("status", "declined").awaitOnline() }
    suspend fun invite(p: PropertyRecord, application: RentalApplication? = null): String {
        val code = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val id = hash(code)
        val property = db.collection("properties").document(p.id)
        val invitation = db.collection("propertyInvites").document(id)
        db.runTransaction { tx ->
            val current = tx.get(property)
            check(current.getString("ownerUid") == uid && current.getString("tenantUid").isNullOrBlank()) { "Primero desvincula al inquilino actual." }
            tx.set(invitation, mapOf("ownerUid" to uid, "propertyId" to p.id, "propertyName" to p.name,
                "targetUid" to (application?.applicantUid ?: ""), "usedBy" to "", "status" to "pending",
                "expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + 7 * 86400000L))))
            tx.update(property, "activeInviteId", id)
            if (application != null) tx.update(db.collection("rentalApplications").document(application.id),
                mapOf("status" to "approved", "inviteCode" to code))
        }.awaitOnline()
        return code
    }
    suspend fun accept(code: String) {
        check(AccountRole.fromStored(role()).requestsRentals) { "Esta invitación es para un inquilino. El encargado necesita un código de asignación." }
        val normalized = code.trim().replace("-", "").lowercase()
        require(normalized.matches(Regex("[a-f0-9]{32}"))) { "El código de invitación debe contener 32 caracteres." }
        val invite = db.collection("propertyInvites").document(hash(normalized))
        try { db.runTransaction { tx ->
            val data = tx.get(invite)
            check(data.exists() && data.getString("status") == "pending" &&
                (data.getTimestamp("expiresAt")?.toDate()?.time ?: 0) > System.currentTimeMillis()) { "La invitación ya se utilizó, caducó o no existe." }
            val target = data.getString("targetUid").orEmpty()
            check(target.isEmpty() || target == uid) { "La invitación está destinada a otra cuenta." }
            val owner = data.getString("ownerUid") ?: error("Invitación inválida")
            check(owner != uid) { "No puedes aceptar tu propia invitación." }
            val propertyId = data.getString("propertyId") ?: error("Invitación inválida")
            val listingRef = db.collection("rentalListings").document(propertyId)
            val listingExists = tx.get(listingRef).exists()
            tx.update(db.collection("properties").document(propertyId),
                mapOf("tenantUid" to uid, "memberUids" to listOf(owner, uid)))
            tx.update(invite, mapOf("status" to "used", "usedBy" to uid))
            if (listingExists) tx.delete(listingRef)
        }.awaitOnline() }
        catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                throw IllegalStateException("La invitación de alquiler ya no está activa o el inmueble dejó de estar disponible. Pide una nueva al propietario.",e)
            throw e
        }
    }
    suspend fun unlink(p: PropertyRecord) { db.collection("properties").document(p.id)
        .update(mapOf("tenantUid" to "", "memberUids" to listOf(uid), "activeInviteId" to "")).awaitOnline() }
    suspend fun share(p: PropertyRecord, inspections: List<CasaInspection>) {
        for (inspection in inspections.filter { it.propertyId == p.id }) {
            val payload = with(CasaStore) { inspection.dataOnlySnapshot().toJson().toString() }
            require(payload.toByteArray().size < 850000) { "La inspección es demasiado grande para compartirla." }
            // Author-specific documents prevent one member overwriting another member's work.
            db.collection("properties").document(p.id).collection("inspections").document("${uid}_${inspection.id}")
                .set(mapOf("authorUid" to uid, "payload" to payload)).awaitOnline()
        }
    }
    suspend fun shared(p: PropertyRecord): List<CasaInspection> = db.collection("properties").document(p.id)
        .collection("inspections").get(Source.SERVER).awaitOnline().documents.map {
            CasaStore.inspectionFromJson(JSONObject(it.getString("payload") ?: error("Inspección sin datos")))
                .dataOnlySnapshot().also { inspection ->
                    val safe = Regex("[A-Za-z0-9_-]{1,128}")
                    check(inspection.propertyId == p.id && safe.matches(inspection.id) &&
                        inspection.rooms.all { room -> safe.matches(room.id) && room.items.all { item -> safe.matches(item.id) } }) {
                        "La inspección compartida contiene identificadores inválidos."
                    }
                }
        }
    companion object {
        fun hash(code: String): String = MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
