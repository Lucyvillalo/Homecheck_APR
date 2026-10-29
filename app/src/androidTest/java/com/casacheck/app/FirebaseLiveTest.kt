package com.casacheck.app

import com.casacheck.app.data.remote.RegistrationIncompleteException
import com.casacheck.app.data.remote.ProfileCompletionRequiredException
import com.casacheck.app.data.remote.describeAccountFailure
import com.casacheck.app.data.remote.CloudInspectionRepository
import com.casacheck.app.data.remote.FirebaseServices
import com.casacheck.app.data.remote.dataOnlySnapshot
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaRoom
import com.casacheck.app.domain.model.Condition

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in: uses only generated, fictitious QA data; never sends email. */
class FirebaseLiveTest {
    @Test fun authenticationDataBackupRestoreAndPrivateRules() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseLive") == "true")
        withTimeout(120_000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertTrue(FirebaseServices.initialize(context))
            val auth = FirebaseAuth.getInstance()
            auth.signOut()
            val email = "homecheck-qa-${UUID.randomUUID()}@example.com"
            val password = "QA-${UUID.randomUUID()}"
            var uid = ""
            try {
                FirebaseServices.register("Verificación QA (datos ficticios)", email, password, "Inquilino", true)
                uid = auth.currentUser!!.uid
                assertTrue(auth.currentUser!!.getIdToken(false).await().token!!.isNotBlank())
                auth.signOut()
                FirebaseServices.signIn(email, password)
                assertEquals(uid, auth.currentUser!!.uid)
                val database = FirebaseFirestore.getInstance()
                val profile = database.collection("usuarios").document(uid).get().await()
                assertEquals("Inquilino", profile.getString("rol"))
                val source = CasaInspection(propertyName = "QA sin inmueble real", address = "Datos ficticios", type = "Casa",
                    rooms = listOf(CasaRoom(name = "Sala QA", items = listOf(CasaItem(name = "Pared QA",
                        condition = Condition.GOOD, quantity = 3, notes = "Prueba técnica", photoPath = "/no-upload/photo.jpg")))),
                    completed = true, ownerSignature = "private-strokes", renterSignature = "private-strokes", reportPath = "/no-upload/report.pdf")
                val repository = CloudInspectionRepository(uid)
                repository.upload(listOf(source))
                val restored = repository.download().single { it.id == source.id }
                assertEquals(source.dataOnlySnapshot(), restored)
                assertTrue(source.completed)
                val stored = database.collection("usuarios").document(uid).collection("inspecciones").document(source.id).get().await()
                assertEquals("DATA_ONLY", stored.getString("backupMode"))
                assertFalse(stored.getString("payload")!!.contains("/no-upload/"))
                assertFalse(stored.getString("payload")!!.contains("private-strokes"))
                try {
                    database.collection("usuarios").document("otro-uid-qa").collection("inspecciones").get().await()
                    fail("Cross-user read must be denied")
                } catch (e: FirebaseFirestoreException) { assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code) }
                try {
                    database.collection("usuarios").document(uid).collection("inspecciones").document("invalid-qa")
                        .set(mapOf("idUsuario" to "otro-uid", "payload" to "{}")).await()
                    fail("Spoofed ownership must be denied")
                } catch (e: FirebaseFirestoreException) { assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code) }
            } finally {
                // Rules deliberately prohibit document deletion. Only fictitious QA records remain.
                // Remove the temporary Auth account and never persist or print its credentials.
                if (uid.isNotBlank() && auth.currentUser?.uid == uid) auth.currentUser!!.delete().await()
                auth.signOut()
            }
        }
    }
    @Test fun interruptedRegistrationCanBeCompletedWithoutCreatingAnotherAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseLive") == "true")
        withTimeout(120_000) {
            assertTrue(FirebaseServices.initialize(InstrumentationRegistry.getInstrumentation().targetContext))
            val auth = FirebaseAuth.getInstance(); auth.signOut()
            val email = "homecheck-retry-${UUID.randomUUID()}@example.com"
            val password = "Qa!${UUID.randomUUID()}"
            var created = false
            try {
                try {
                    // Deliberately invalid fixture role: published rules must reject the profile.
                    FirebaseServices.register("Cuenta ficticia de recuperación", email, password, "ROL_QA_NO_PERMITIDO", true)
                    fail("Invalid profile role must be rejected")
                } catch (error: RegistrationIncompleteException) {
                    created = true
                    assertTrue(describeAccountFailure(error).code.contains("PERMISSION_DENIED"))
                    assertNull(auth.currentUser)
                }
                try {
                    FirebaseServices.signIn(email, password)
                    fail("Missing profile must request completion")
                } catch (error: ProfileCompletionRequiredException) { assertNull(auth.currentUser) }
                FirebaseServices.completeRegistration("Cuenta ficticia de recuperación", email, password, "Inquilino", true)
                val uid = auth.currentUser!!.uid
                val profile = FirebaseFirestore.getInstance().collection("usuarios").document(uid).get().await()
                assertEquals("Inquilino", profile.getString("rol"))
                FirebaseServices.completeRegistration("Cuenta ficticia de recuperación", email, password, "Propietario", true)
                assertEquals(uid, auth.currentUser!!.uid)
                assertEquals("Inquilino", FirebaseFirestore.getInstance().collection("usuarios").document(uid).get().await().getString("rol"))
            } finally {
                if (created) {
                    if (auth.currentUser?.email != email) auth.signInWithEmailAndPassword(email, password).await()
                    auth.currentUser!!.delete().await()
                }
                auth.signOut()
            }
        }
    }

}
