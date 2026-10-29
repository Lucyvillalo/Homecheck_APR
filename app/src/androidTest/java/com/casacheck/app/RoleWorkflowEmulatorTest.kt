package com.casacheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.casacheck.app.data.local.CasaStore
import com.casacheck.app.data.remote.*
import com.casacheck.app.ui.HomeCheckApp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.util.UUID
import java.io.File
import android.graphics.Bitmap

/** Three genuine roles, SDK + Compose, only official local emulators. */
class RoleWorkflowEmulatorTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private data class Actor(val repo: PropertyRepository, val staff: StaffAssignmentRepository, val auth: FirebaseAuth)
    private val actors = mutableListOf<Actor>()
    @Before fun optIn() { assumeTrue(InstrumentationRegistry.getArguments().getString("propertiesEmulator") == "true") }
    private suspend fun actor(role: String, main: Boolean = false): Actor {
        val existing = if (main) runCatching { FirebaseApp.getInstance() }.getOrNull() else null
        val fresh = existing == null
        val options = FirebaseOptions.Builder().setProjectId("demo-homecheck-reset")
            .setApplicationId("1:123456789:android:roleqa").setApiKey("fake-api-key").build()
        val app = existing ?: if(main) FirebaseApp.initializeApp(context,options)
            else FirebaseApp.initializeApp(context,options,"role-"+UUID.randomUUID())
        check(app.options.projectId == "demo-homecheck-reset")
        val auth=FirebaseAuth.getInstance(app); val db=FirebaseFirestore.getInstance(app)
        if (fresh) { auth.useEmulator("10.0.2.2",9099); db.useEmulator("10.0.2.2",8080) }
        check(db.firestoreSettings.host == "10.0.2.2:8080")
        auth.signOut()
        val uid=auth.createUserWithEmailAndPassword("roles-${UUID.randomUUID()}@example.com","EmulatorRole!2026").await().user!!.uid
        db.collection("usuarios").document(uid).set(mapOf("idUsuario" to uid,"rol" to role)).await()
        return Actor(PropertyRepository(auth,db),StaffAssignmentRepository(auth,db),auth).also { actors.add(it) }
    }
    private fun <T> remote(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(60_000) { block() } }
    private fun awaitText(text: String) { compose.waitUntil(40_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory=File(context.getExternalFilesDir(null),"role-qa").also { it.mkdirs() }
        File(directory,name+".png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
        image.recycle()
    }
    @After fun cleanup() { actors.forEach { it.auth.signOut() } }

    @Test fun choosingOwnerPortalDoesNotPromoteAnExistingStaffAccount() {
        val staff=remote { actor("Encargado",true) }
        val uid=staff.auth.currentUser!!.uid
        val email=staff.auth.currentUser!!.email!!
        staff.auth.signOut()
        compose.setContent { HomeCheckApp() }
        compose.onNodeWithText("Propietario",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Continuar",substring=false).performClick()
        compose.onNodeWithText("Correo electrónico").performTextInput(email)
        compose.onNodeWithText("Contraseña",substring=false).performTextInput("EmulatorRole!2026")
        compose.onNodeWithText("Iniciar sesión →").performScrollTo().performClick()
        awaitText("Panel del encargado")
        screenshot("staff-home")
        assertEquals(uid,staff.auth.currentUser!!.uid)
        assertEquals("Encargado",remote { staff.repo.role() })
        compose.onNodeWithText("Mis propiedades").assertDoesNotExist()
        compose.onNodeWithText("Solicitudes recibidas").assertDoesNotExist()
        compose.onNodeWithText("Espacios asignados").performClick()
        awaitText("Perfil de la cuenta: Encargado")
        compose.onNodeWithText("Registrar propiedad").assertDoesNotExist()
    }

    @Test fun ownerWorkspaceShowsOnlyOwnAdvertisementsAndManagement() {
        val owner=remote { actor("Propietario",true) }; val other=remote { actor("Propietario") }
        val title="Mi inmueble ${UUID.randomUUID().toString().take(6)}"
        remote { owner.repo.create(title,"Dirección propietario 123","Casa") }
        val property=remote { owner.repo.properties().single() }
        remote { owner.repo.publish(property,"Mi zona","Descripción privada de prueba pública","400") }
        remote { other.repo.create("Anuncio de otro propietario","Otra dirección 123","Casa") }
        remote { other.repo.publish(other.repo.properties().single(),"Otra zona","Descripción pública de otro propietario","500") }
        compose.setContent { HomeCheckApp() }
        awaitText("Panel del propietario")
        screenshot("owner-home")
        compose.onNodeWithText("Mis propiedades").performClick()
        awaitText("Registrar propiedad")
        compose.onNodeWithText("Invitar encargado").performScrollTo().assertExists()
        compose.onNodeWithText("Aceptar encargo").assertDoesNotExist()
        compose.onNodeWithText("Perfil de la cuenta: Propietario").performScrollTo()
        compose.onNodeWithText("Mis anuncios").performClick()
        awaitText(title)
        compose.onNodeWithText("Anuncio de otro propietario").assertDoesNotExist()
        compose.onNodeWithText("Solicitar alquiler").assertDoesNotExist()
    }

    @Test fun tenantRequestsAcceptsAndPropertyDisappearsFromCatalog() {
        val tenant=remote { actor("Inquilino",true) }; val owner=remote { actor("Propietario") }
        val title="Alquiler ${UUID.randomUUID().toString().take(8)}"
        remote { owner.repo.create(title,"Dirección alquiler 123","Apartamento") }
        val property=remote { owner.repo.properties().single() }
        remote { owner.repo.publish(property,"Zona alquiler","Descripción suficiente de este alquiler","350.50") }
        compose.setContent { HomeCheckApp() }
        awaitText("Panel del inquilino")
        screenshot("tenant-home")
        compose.onNodeWithText("Buscar alquiler").performClick()
        awaitText("Buscar por nombre o zona")
        compose.onNodeWithText("Buscar por nombre o zona").performTextInput(title)
        compose.onNodeWithText("Registrar propiedad").assertDoesNotExist()
        compose.onNodeWithText("Solicitar alquiler").performScrollTo().performClick()
        compose.waitUntil(40_000) { remote { owner.repo.applications(true).any { it.propertyId==property.id } } }
        val request=remote { owner.repo.applications(true).single { it.propertyId==property.id } }
        remote { owner.repo.invite(property,request) }
        compose.onNodeWithText("Perfil de la cuenta: Inquilino").performScrollTo()
        compose.onNodeWithText("Mis solicitudes").performClick()
        compose.onNodeWithText("Actualizar").performScrollTo().performClick()
        awaitText("Aceptar vinculación")
        compose.onNodeWithText("Aceptar vinculación").performScrollTo().performClick()
        awaitText("Vinculación de alquiler activa")
        assertTrue(remote { tenant.repo.listings().none { it.id==property.id } })
        assertEquals(tenant.auth.currentUser!!.uid,remote { owner.repo.properties().single().tenantUid })
        compose.onNodeWithText("Perfil de la cuenta: Inquilino").performScrollTo()
        compose.onAllNodesWithText("Mis alquileres").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Invitar encargado").assertDoesNotExist()
        compose.onNodeWithText("Publicar o actualizar anuncio").assertDoesNotExist()
    }

    @Test fun staffNeedsInvitationCreatesAssignedInspectionAndLosesAccessOnRevocation() {
        val staff=remote { actor("Encargado",true) }; val owner=remote { actor("Propietario") }; val tenant=remote { actor("Inquilino") }
        remote { owner.repo.create("Espacio delegado","Dirección del encargo 123","Salón") }
        var property=remote { owner.repo.properties().single() }
        val tenantCode=remote { owner.repo.invite(property) }
        remote { tenant.repo.accept(tenantCode) }
        property=remote { owner.repo.properties().single() }
        val replacedCode=remote { owner.staff.invite(property) }
        val code=remote { owner.staff.invite(property) }
        compose.setContent { HomeCheckApp() }
        awaitText("Panel del encargado")
        compose.onNodeWithText("Buscar alquiler").assertDoesNotExist()
        compose.onNodeWithText("Solicitudes recibidas").assertDoesNotExist()
        compose.onNodeWithText("Nueva inspección",substring=false).performClick()
        awaitText("Aceptar encargo")
        compose.onNodeWithText("Registrar propiedad").assertDoesNotExist()
        compose.onNodeWithText("Espacio delegado").assertDoesNotExist()
        compose.onNodeWithText("Código de invitación").performTextInput(replacedCode)
        compose.onNodeWithText("Aceptar encargo").performScrollTo().performClick()
        awaitText("El propietario retiró o sustituyó esta invitación de encargado. Pídele un código nuevo.")
        compose.onNodeWithText("Código de invitación").performScrollTo().performTextClearance()
        compose.onNodeWithText("Código de invitación").performTextInput(code)
        compose.onNodeWithText("Aceptar encargo").performScrollTo().performClick()
        awaitText("Espacio delegado")
        compose.onNodeWithText("Publicar o actualizar anuncio").assertDoesNotExist()
        compose.onNodeWithText("Invitar inquilino").assertDoesNotExist()
        compose.onNodeWithText("Crear inspección de esta propiedad").performScrollTo().performClick()
        awaitText("Ambientes")
        val uid=staff.auth.currentUser!!.uid
        compose.waitUntil(30_000) { CasaStore.load(context,uid).any { it.propertyId==property.id } }
        val inspection=CasaStore.load(context,uid).single { it.propertyId==property.id }
        remote { staff.repo.share(property,listOf(inspection)) }
        assertEquals(inspection.id,remote { owner.repo.shared(property).single().id })
        val assignment=remote { owner.staff.ownedAssignments().single { it.staffUid==uid } }
        remote { owner.staff.revoke(assignment) }
        assertEquals(tenant.auth.currentUser!!.uid,remote { owner.repo.properties().single().tenantUid })
        assertNotNull(runCatching { remote { staff.repo.shared(property) } }.exceptionOrNull())
        compose.onNodeWithText("‹",substring=false).performClick()
        compose.onNodeWithText("Inicio",substring=false).performClick()
        compose.onNodeWithText("Espacios asignados").performClick()
        awaitText("No tienes espacios asignados. El propietario debe invitarte antes de crear inspecciones.")
        compose.onNodeWithText("Espacio delegado").assertDoesNotExist()
    }
}
