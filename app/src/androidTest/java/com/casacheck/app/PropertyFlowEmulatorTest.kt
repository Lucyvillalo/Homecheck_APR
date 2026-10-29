package com.casacheck.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.casacheck.app.data.remote.*
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.roomsFor
import com.casacheck.app.ui.property.PropertiesScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.util.UUID

/** SDK + Compose integration. Explicit opt-in, only demo emulators, never production. */
class PropertyFlowEmulatorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun ownerPublishesTenantAppliesAndSharesPrivateInspection() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("propertiesEmulator") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun app(name: String?): FirebaseApp {
            val options = FirebaseOptions.Builder().setProjectId("demo-homecheck-reset")
                .setApplicationId("1:123456789:android:propertyqa").setApiKey("fake-api-key").build()
            return if(name == null) FirebaseApp.initializeApp(context, options) else FirebaseApp.initializeApp(context, options, name)
        }
        suspend fun actor(role: String, main: Boolean): Pair<PropertyRepository, FirebaseAuth> {
            val instance = app(if(main) null else UUID.randomUUID().toString())
            val auth = FirebaseAuth.getInstance(instance); auth.useEmulator("10.0.2.2",9099)
            val db = FirebaseFirestore.getInstance(instance); db.useEmulator("10.0.2.2",8080)
            val uid = auth.createUserWithEmailAndPassword("property-${UUID.randomUUID()}@example.com", "Emulator!2026").await().user!!.uid
            db.collection("usuarios").document(uid).set(mapOf("idUsuario" to uid,"rol" to role)).await()
            return PropertyRepository(auth,db) to auth
        }
        val (owner, ownerAuth) = actor("Propietario", true)
        val (tenant, tenantAuth) = actor("Inquilino", false)
        try {
            owner.create("Casa prueba SDK", "Dirección privada de prueba 123", "Casa")
            val property = owner.properties().single()
            owner.publish(property, "Zona QA", "Descripción pública sin dirección privada", "450.50")
            val listing = tenant.listings().single { it.id == property.id }
            assertEquals(45050L, listing.priceCents)
            tenant.apply(listing)
            val request = owner.applications(true).single { it.propertyId == property.id }
            owner.invite(property,request)
            val approved = tenant.applications(false).single { it.propertyId == property.id }
            assertEquals("approved",approved.status)
            tenant.accept(approved.inviteCode)
            assertEquals(tenantAuth.currentUser!!.uid, tenant.properties().single().tenantUid)
            val inspection = CasaInspection(propertyId=property.id,propertyName=property.name,address=property.address,
                type=property.type,rooms=roomsFor("Casa"),ownerSignature="PRIVATE_SIGNATURE",reportPath="PRIVATE_PATH")
            tenant.share(property,listOf(inspection))
            val shared = owner.shared(property).single()
            assertEquals(inspection.id,shared.id)
            assertTrue(shared.ownerSignature.isEmpty()); assertTrue(shared.reportPath.isEmpty()); assertFalse(shared.completed)
            compose.setContent { MaterialTheme { PropertiesScreen(emptyList(), {}, {}) } }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Casa prueba SDK").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Perfil de la cuenta: Propietario").assertExists()
            compose.onNodeWithText("Registrar propiedad").assertExists()
            compose.onNodeWithText("Casa prueba SDK").assertExists()
            owner.withdraw(property)
            assertTrue(tenant.listings().none { it.id == property.id })
            owner.unlink(property)
            assertTrue(tenant.properties().isEmpty())
            val denied = runCatching { tenant.shared(property) }.exceptionOrNull()
            assertNotNull(denied)
        } finally { ownerAuth.signOut(); tenantAuth.signOut() }
    }
}
