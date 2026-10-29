package com.casacheck.app

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.casacheck.app.ui.theme.HomeTypography
import com.casacheck.app.ui.theme.Navy
import com.casacheck.app.ui.theme.Blue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.casacheck.app.data.remote.FirebasePasswordRecoveryRepository
import com.casacheck.app.domain.account.PasswordRecoveryGateway
import com.casacheck.app.ui.account.PasswordRecoveryScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Local official Auth emulator only; no production accounts, email delivery or billing. */
class PasswordRecoveryEmulatorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var recovery: PasswordRecoveryGateway
    private lateinit var email: String
    private val oldPassword = "Old-Qa!12345678"
    private val newPassword = "New-Qa!87654321"
    private val project = "demo-homecheck-reset"
    @Before fun setup() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryEmulator") == "true")
        app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext,
            FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:resetqa").setApiKey("fake-api-key").build(), "reset-${UUID.randomUUID()}")
        auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("10.0.2.2", 9099)
        recovery = FirebasePasswordRecoveryRepository(auth)
        email = "reset-${UUID.randomUUID()}@example.com"
        withTimeout(30_000) { auth.createUserWithEmailAndPassword(email, oldPassword).await() }
        auth.signOut()
    }
    @After fun cleanup() { if (::app.isInitialized) auth.signOut() } // Named apps live until the instrumentation process exits; Auth caches emulator routing by API key.
    private fun resetLink(): String {
        val connection = URL("http://10.0.2.2:9099/emulator/v1/projects/$project/oobCodes").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        try {
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("oobCodes")
            for (index in data.length() - 1 downTo 0) {
                val item = data.getJSONObject(index)
                if (item.getString("email") == email) return "https://$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=${item.getString("oobCode")}&lang=es"
            }
            error("No password-reset code generated for QA fixture")
        } finally { connection.disconnect() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(350)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.filesDir, "qa-recovery-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun changesPasswordRejectsOldPasswordAndRejectsReusedLink() = runBlocking {
        withTimeout(60_000) {
            recovery.request(email)
            val link = resetLink()
            val reset = recovery.verify(link)
            assertEquals(email, reset.email)
            recovery.confirm(reset, newPassword)
            try { auth.signInWithEmailAndPassword(email, oldPassword).await(); fail("Old password still accepted") }
            catch (expected: FirebaseAuthException) { assertTrue(expected.errorCode in setOf("ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS")) }
            assertEquals(email, auth.signInWithEmailAndPassword(email, newPassword).await().user!!.email)
            auth.signOut()
            try { recovery.verify(link); fail("Used link was accepted twice") }
            catch (expected: FirebaseAuthException) { assertTrue(expected.errorCode in setOf("ERROR_INVALID_ACTION_CODE", "ERROR_EXPIRED_ACTION_CODE")) }
        }
    }
    @Test fun dedicatedScreensCompleteTheResetAndAllowLoginWithNewPassword() {
        var completed = ""
        var incoming by mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme(typography = HomeTypography, colorScheme = lightColorScheme(primary = Navy, secondary = Blue, surface = Color.White)) { PasswordRecoveryScreen(true, email, {}, { completed = it }, recovery, initialLink = incoming) } }
        compose.onNodeWithText("Recuperar contraseña").assertExists()
        capture("request")
        compose.onNodeWithText("Enviar enlace de recuperación").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Revisa tu correo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("recovery-resend").assertIsNotEnabled()
        capture("sent")
        val link = resetLink()
        compose.runOnIdle { incoming = "https://$project.firebaseapp.com/__/auth/links?link=${java.net.URLEncoder.encode(link, "UTF-8")}" }
        compose.onNodeWithText("Enlace de recuperación").assertDoesNotExist()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Crear nueva contraseña").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nueva contraseña", substring = false).performScrollTo().performTextInput(newPassword)
        compose.onNodeWithText("Fortaleza: Fuerte").assertExists()
        compose.onNodeWithText("Confirmar nueva contraseña").performScrollTo().performTextInput(newPassword)
        capture("new-password")
        compose.onNodeWithText("Guardar nueva contraseña").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Contraseña actualizada").fetchSemanticsNodes().isNotEmpty() }
        capture("done")
        compose.onNodeWithText("Volver a iniciar sesión").performScrollTo().performClick()
        assertEquals(email, completed)
        runBlocking { withTimeout(30_000) { assertEquals(email, auth.signInWithEmailAndPassword(email, newPassword).await().user!!.email) } }
    }
    @Test fun showsAnActionableErrorForAConsumedLink() = runBlocking {
        recovery.request(email)
        val link = resetLink()
        recovery.confirm(recovery.verify(link), newPassword)
        compose.setContent { MaterialTheme(typography = HomeTypography, colorScheme = lightColorScheme(primary = Navy, secondary = Blue, surface = Color.White)) { PasswordRecoveryScreen(true, email, {}, {}, recovery, initialLink = link) } }

        compose.waitUntil(30_000) { compose.onAllNodesWithText("El enlace no es válido o ya se utilizó.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Solicitar otro enlace").assertExists()
        compose.onNodeWithText("Nueva contraseña", substring = false).assertDoesNotExist()
        capture("used-link")
    }
}
