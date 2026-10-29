package com.casacheck.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.casacheck.app.data.remote.FirebasePasswordRecoveryRepository
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Real Android Activity lifecycle with Auth emulator; OS domain verification is checked separately. */
class RecoveryIntentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var email: String
    private lateinit var link: String
    private lateinit var uid: String
    private val oldPassword = "Old-Qa!12345678"
    private val newPassword = "New-Qa!87654321"
    private val project = "demo-homecheck-reset"
    @Before fun setup() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryEmulator") == "true")
        val app = runCatching { FirebaseApp.getInstance() }.getOrNull() ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:resetintentqa").setApiKey("fake-api-key").build())
        check(app.options.projectId == project)
        auth = FirebaseAuth.getInstance(app); auth.useEmulator("10.0.2.2", 9099); auth.signOut()
        email = "intent-${UUID.randomUUID()}@example.com"
        withTimeout(30_000) {
            uid = auth.createUserWithEmailAndPassword(email, oldPassword).await().user!!.uid
            auth.signOut()
            FirebasePasswordRecoveryRepository(auth).request(email)
        }
        val connection = URL("http://10.0.2.2:9099/emulator/v1/projects/$project/oobCodes").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        val code = try {
            val items = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("oobCodes")
            (items.length() - 1 downTo 0).map { items.getJSONObject(it) }.first { it.getString("email") == email }.getString("oobCode")
        } finally { connection.disconnect() }
        val action = "https://$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=$code"
        link = "https://$project.firebaseapp.com/__/auth/links?link=${URLEncoder.encode(action, "UTF-8")}&apn=com.casacheck.app"
    }
    @After fun cleanup() { if (::auth.isInitialized) auth.signOut() }
    private fun viewIntent(value: String) = Intent(Intent.ACTION_VIEW, Uri.parse(value), context, MainActivity::class.java)
        .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // ActivityScenario compares action/data with the initial Intent and drops lifecycle events
    // after a legitimate onNewIntent/setIntent. Observe actual Activity instances instead.
    // https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun launch(intent: Intent): MainActivity = instrumentation.startActivitySync(
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    private fun resumedActivity(): MainActivity? {
        var current: MainActivity? = null
        instrumentation.runOnMainSync {
            current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().singleOrNull()
        }
        return current
    }
    private fun recreate() {
        val previous = checkNotNull(resumedActivity())
        instrumentation.runOnMainSync { previous.recreate() }
        compose.waitUntil(30_000) { resumedActivity()?.let { it !== previous } == true }
        assertNotSame(previous, resumedActivity())
    }
    private fun finishActivities() {
        instrumentation.runOnMainSync {
            listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED, Stage.STOPPED, Stage.CREATED)
                .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .filterIsInstance<MainActivity>().distinct().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }
    private fun awaitForm() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Crear nueva contraseña").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Enlace de recuperación").assertDoesNotExist()
        compose.onNodeWithText("¿Cómo usarás la app?").assertDoesNotExist()
    }
    @Test fun coldStartLinkOpensResetWithoutCopyingAndChangesPassword() {
        launch(viewIntent(link))
        try {
            awaitForm()
            compose.onNodeWithText("Nueva contraseña", substring = false).performScrollTo().performTextInput(newPassword)
            compose.onNodeWithText("Confirmar nueva contraseña").performScrollTo().performTextInput(newPassword)
            compose.onNodeWithText("Guardar nueva contraseña").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Contraseña actualizada").fetchSemanticsNodes().isNotEmpty() }
            recreate()
            compose.onNodeWithText("Contraseña actualizada").assertExists()
            compose.onNodeWithText("Volver a iniciar sesión").performScrollTo().performClick()
            compose.onNodeWithText("Correo electrónico").assertTextContains(email)
            compose.onNodeWithText("Contraseña", substring = false).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            runBlocking { withTimeout(30_000) {
                try { auth.signInWithEmailAndPassword(email, oldPassword).await(); fail("Old password accepted") }
                catch (expected: FirebaseAuthException) { }
                assertEquals(uid, auth.signInWithEmailAndPassword(email, newPassword).await().user!!.uid)
            } }
        } finally { finishActivities() }
    }
    @Test fun openAppReceivesNewIntentAndRejectsAReplacementUntrustedLink() {
        val initialActivity = launch(Intent(context, MainActivity::class.java))
        try {
            compose.onNodeWithText("Continuar", substring = false).performClick()
            context.startActivity(viewIntent(link))
            awaitForm()
            assertSame(initialActivity, resumedActivity())
            compose.onNodeWithText("Nueva contraseña", substring = false).performScrollTo().performTextInput("NotSaved-Qa!123")
            recreate()
            awaitForm()
            compose.onNodeWithText("Nueva contraseña", substring = false).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            val foreign = "https://another-project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=fake-qa-only-code"
            context.startActivity(viewIntent("https://$project.firebaseapp.com/__/auth/links?link=${URLEncoder.encode(foreign, "UTF-8")}"))
            compose.waitUntil(30_000) { compose.onAllNodesWithText("No se pudo abrir el enlace").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nueva contraseña", substring = false).assertDoesNotExist()
            compose.onNodeWithText("Solicitar otro enlace").assertExists()
            assertNull(auth.currentUser)
        } finally { finishActivities() }
    }
}
