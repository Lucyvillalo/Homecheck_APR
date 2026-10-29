package com.casacheck.app.data.remote

import com.casacheck.app.domain.account.AccountRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.security.SecureRandom

/** Operational delegation is separate from ownership and the tenant's rental agreement. */
data class StaffAssignment(val id: String, val propertyId: String, val staffUid: String, val staffName: String)
class StaffAssignmentRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val uid get() = requireNotNull(auth.currentUser).uid
    suspend fun assignedProperties(): List<PropertyRecord> {
        val assignments = db.collection("staffAssignments").whereEqualTo("staffUid",uid)
            .get(Source.SERVER).awaitOnline().documents
        return assignments.map { a ->
            val p = db.collection("properties").document(a.getString("propertyId") ?: error("Asignación inválida"))
                .get(Source.SERVER).awaitOnline()
            PropertyRecord(p.id,p.getString("ownerUid").orEmpty(),p.getString("name").orEmpty(),
                p.getString("address").orEmpty(),p.getString("type").orEmpty(),p.getString("tenantUid").orEmpty())
        }.distinctBy { it.id }
    }
    /** Read-only compatibility: never turn a former staff-created record into a new ownership grant. */
    suspend fun legacyProperties(): List<PropertyRecord> = db.collection("properties").whereEqualTo("ownerUid",uid)
        .get(Source.SERVER).awaitOnline().documents.map { p ->
            PropertyRecord(p.id,p.getString("ownerUid").orEmpty(),p.getString("name").orEmpty(),p.getString("address").orEmpty(),p.getString("type").orEmpty(),p.getString("tenantUid").orEmpty())
        }
    suspend fun ownedAssignments(): List<StaffAssignment> = db.collection("staffAssignments")
        .whereEqualTo("ownerUid",uid).get(Source.SERVER).awaitOnline().documents.map {
            StaffAssignment(it.id,it.getString("propertyId").orEmpty(),it.getString("staffUid").orEmpty(),it.getString("staffName").orEmpty())
        }
    suspend fun invite(property: PropertyRecord): String {
        check(AccountRole.fromStored(PropertyRepository(auth,db).role()) == AccountRole.OWNER) { "Solo el propietario puede asignar encargados." }
        check(property.ownerUid == uid) { "Solo el propietario puede asignar un encargado." }
        val code = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val id = PropertyRepository.hash(code)
        val batch = db.batch()
        batch.update(db.collection("properties").document(property.id),"activeStaffInviteId",id)
        batch.set(db.collection("staffInvites").document(id),mapOf("ownerUid" to uid,"propertyId" to property.id,
            "propertyName" to property.name,"status" to "pending","usedBy" to "",
            "expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis()+7*86400000L))))
        batch.commit().awaitOnline()
        return code
    }
    suspend fun accept(code: String) {
        check(AccountRole.fromStored(PropertyRepository(auth,db).role()) == AccountRole.STAFF) { "Esta invitación es exclusivamente para una cuenta Encargado." }
        val normalized = code.trim().replace("-", "").lowercase()
        require(normalized.matches(Regex("[a-f0-9]{32}"))) { "El código de encargado debe contener 32 caracteres." }
        val id = PropertyRepository.hash(normalized)
        val ref = db.collection("staffInvites").document(id)
        try { db.runTransaction { tx ->
            val invite = tx.get(ref)
            check(invite.exists() && invite.getString("status") == "pending" &&
                (invite.getTimestamp("expiresAt")?.toDate()?.time ?: 0)>System.currentTimeMillis()) {
                "La invitación de encargado caducó, ya fue utilizada o no existe. Pide una nueva al propietario."
            }
            val owner = invite.getString("ownerUid") ?: error("Invitación inválida")
            check(owner != uid) { "No puedes asignarte tu propia propiedad como encargado." }
            val propertyId = invite.getString("propertyId") ?: error("Invitación inválida")
            tx.set(db.collection("staffAssignments").document("${propertyId}_$uid"),mapOf("propertyId" to propertyId,
                "ownerUid" to owner,"staffUid" to uid,"staffName" to auth.currentUser?.displayName.orEmpty().take(100),"inviteId" to id))
            tx.update(ref,mapOf("status" to "used","usedBy" to uid))
        }.awaitOnline() }
        catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                throw IllegalStateException("El propietario retiró o sustituyó esta invitación de encargado. Pídele un código nuevo.",e)
            throw e
        }
    }
    suspend fun revoke(assignment: StaffAssignment) {
        val batch = db.batch()
        batch.delete(db.collection("staffAssignments").document(assignment.id))
        batch.update(db.collection("properties").document(assignment.propertyId),"activeStaffInviteId", "")
        batch.commit().awaitOnline()
    }
}
