package com.casacheck.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.casacheck.app.data.remote.FirebaseServices
import com.casacheck.app.ui.HomeCheckApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in test with generated credentials; never uses a real person's account or sends mail. */
class AccountRegistrationLiveTest {
    @get:Rule val compose = createComposeRule()
    private fun registerForm(email: String, password: String) {
        compose.onNodeWithText("¿No tienes cuenta? Regístrate").performScrollTo().performClick()
        compose.onNodeWithText("Nombre completo").performScrollTo().performTextInput("Cuenta ficticia QA")
        compose.onNodeWithText("Correo electrónico").performScrollTo().performTextInput(email)
        compose.onNodeWithText("Contraseña", substring = false).performScrollTo().performTextInput(password)
        compose.onNodeWithText("Fortaleza: Fuerte").assertExists()
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().performTextInput(password)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Registrarme").performScrollTo().assertIsEnabled().performClick()
    }
    private fun logout() {
        compose.onNodeWithText("Cuenta", substring = false).performClick()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
    }
    @Test fun createsAccountEntersAppLogsInAndExplainsDuplicateEmail() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(FirebaseServices.initialize(instrumentation.targetContext))
        val auth = FirebaseAuth.getInstance(); auth.signOut()
        val email = "homecheck-ui-${UUID.randomUUID()}@example.com"
        val password = "Qa!${UUID.randomUUID()}"
        var accountCreated = false
        try {
            compose.setContent { HomeCheckApp() }
            compose.onNodeWithText("Continuar", substring = false).performClick()
            compose.onNodeWithText("Continuar en modo local").assertDoesNotExist()
            registerForm(email, password)
            compose.waitUntil(60_000) { compose.onAllNodesWithText("Nueva inspección").fetchSemanticsNodes().isNotEmpty() }
            accountCreated = true
            assertNotNull(auth.currentUser)
            val uid = auth.currentUser!!.uid
            logout()
            compose.onNodeWithText("Correo electrónico").performScrollTo().performTextInput(email)
            compose.onNodeWithText("Contraseña", substring = false).performScrollTo().performTextInput(password)
            compose.onNodeWithText("Iniciar sesión →").performScrollTo().performClick()
            compose.waitUntil(60_000) { compose.onAllNodesWithText("Nueva inspección").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(uid, auth.currentUser!!.uid)
            logout()
            registerForm(email, password)
            compose.waitUntil(60_000) { compose.onAllNodesWithText("Este correo ya está registrado.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Este correo ya está registrado.", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("No se pudo completar el acceso. Revisa tus datos y la conexión.").assertDoesNotExist()
            compose.waitForIdle(); instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(350)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(instrumentation.targetContext.filesDir, "qa-registration-duplicate.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            runBlocking { withTimeout(30_000) {
                if (accountCreated) {
                    if (auth.currentUser?.email != email) auth.signInWithEmailAndPassword(email, password).await()
                    auth.currentUser?.delete()?.await()
                }
                auth.signOut()
            } }
        }
    }
}
