package com.casacheck.app

import com.casacheck.app.data.remote.FirebaseServices
import com.casacheck.app.data.local.CasaStore
import com.casacheck.app.domain.model.Condition

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import android.Manifest
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.casacheck.app.ui.CasaCheckApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectionJourneyTest {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val compose = createComposeRule()
    private fun startInspectionFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(FirebaseServices.initialize(context)) { "Configure Firebase before device tests" }
        compose.setContent { CasaCheckApp("qa-journey") {} }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350) // Let the Android 24 compositor present the settled frame.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "qa-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun createsResidentialInspectionAndRejectsUnsignedIncompleteReport() {
        startInspectionFixture()
        capture("dashboard")
        compose.onNodeWithText("Nueva inspección").performClick()
        capture("templates")
        compose.onNodeWithText("Nombre del inmueble").performScrollTo().performTextInput("Casa QA")
        compose.onNodeWithText("Dirección").performScrollTo().performTextInput("Dirección de prueba")
        compose.onNodeWithText("Crear inspección Antes  →").performScrollTo().performClick()
        compose.onNodeWithText("Agregar ambiente", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Nombre del ambiente").performTextInput("Terraza QA")
        compose.onNodeWithText("Agregar").performClick()
        compose.onNodeWithText("Terraza QA").performScrollTo().assertExists()
        capture("rooms")
        compose.onNodeWithText("Revisar resumen  →").performScrollTo().performClick()
        compose.onNodeWithText("Firmar y generar acta").performScrollTo().assertIsNotEnabled()
        capture("summary")
    }

    @Test fun guidedInspectionSavesACameraPhotoConditionAndQuantity() {
        val propertyName = "Captura QA ${java.util.UUID.randomUUID()}"
        startInspectionFixture()
        compose.onNodeWithText("Nueva inspección").performClick()
        compose.onNodeWithText("Nombre del inmueble").performScrollTo().performTextInput(propertyName)
        compose.onNodeWithText("Dirección").performScrollTo().performTextInput("Dirección de prueba")
        compose.onNodeWithText("Crear inspección Antes  →").performScrollTo().performClick()
        compose.onNodeWithText("Habitación principal").performClick()
        compose.onNodeWithText("Paredes").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("capture-photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("capture-photo").performScrollTo()
        org.junit.Assert.assertTrue("Capture controls must sit below the camera preview",
            compose.onNodeWithTag("capture-photo").fetchSemanticsNode().boundsInRoot.top >=
                compose.onNodeWithTag("camera-preview").fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNodeWithTag("capture-photo").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Evidencia capturada · 1").fetchSemanticsNodes().isNotEmpty() }
        capture("embedded-camera")
        compose.onNodeWithText("Cantidad (0–9999)").performScrollTo()
        compose.onNodeWithText("Bueno").assertIsDisplayed().performClick()
        compose.onNodeWithText("Bueno").assertIsSelected()
        compose.onNodeWithText("Cantidad (0–9999)").performScrollTo().performTextClearance()
        compose.onNodeWithText("Cantidad (0–9999)").performTextInput("5")
        compose.onNodeWithText("Observaciones").performScrollTo().performTextInput("Inspección con cámara QA")
        capture("before-save")
        compose.onRoot().printToLog("HomecheckSaveQA")
        compose.onNodeWithText("Guardar elemento  ✓").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Bueno · 5 unidad(es)").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitUntil(10_000) {
            CasaStore.load(context, "qa-journey").firstOrNull { it.propertyName == propertyName }?.rooms?.flatMap { it.items }
                ?.any { it.name == "Paredes" && it.condition == Condition.GOOD && it.quantity == 5 && File(it.photoPath).isFile } == true
        }
    }
}
