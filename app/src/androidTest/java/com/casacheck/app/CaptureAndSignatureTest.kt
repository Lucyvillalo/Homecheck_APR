package com.casacheck.app

import com.casacheck.app.core.parseSignature
import com.casacheck.app.data.evidence.cameraFile
import com.casacheck.app.ui.inspection.CameraPanel
import com.casacheck.app.ui.report.SignaturePad

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CaptureAndSignatureTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun embeddedCameraCapturesARealFrameFromTheEmulatedSensor() {
        var result: File? = null
        var origin = ""
        compose.setContent { MaterialTheme { CameraPanel(false) { file, source -> result = file; origin = source } } }
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("capture-photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("capture-photo").assertIsEnabled().performClick()
        compose.waitUntil(30_000) { result?.isFile == true && result!!.length() > 0 }
        val image = BitmapFactory.decodeFile(result!!.path)
        assertNotNull(image)
        assertTrue(image.width >= 320 && image.height >= 240)
        assertEquals("CAMERA", origin)
        image.recycle(); result!!.delete()
    }

    @Test fun signatureGestureWorksInsideAScrollingFormAndCanBeCleared() {
        var signature by mutableStateOf("")
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { SignaturePad(signature, { signature = it }, "Firma de prueba") }
        } }
        compose.onNodeWithTag("Firma de prueba").performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(signature.isBlank()) }
        compose.onNodeWithTag("Firma de prueba").performTouchInput {
            swipe(Offset(center.x * .2f, center.y * .5f), Offset(center.x * 1.7f, center.y * 1.3f), 500)
        }
        compose.runOnIdle { assertTrue(parseSignature(signature).flatten().size > 1) }
        compose.onNodeWithText("Borrar").performClick()
        compose.runOnIdle { assertTrue(signature.isBlank()) }
    }

    @Test fun galleryImportsTheSelectedFileAndPreservesItsOrigin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = cameraFile(context)
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", fixture)
        val filter = IntentFilter(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("image/*") }
        val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        var imported: File? = null
        var origin = ""
        try {
            compose.setContent { MaterialTheme { CameraPanel(false) { file, source -> imported = file; origin = source } } }
            compose.onNodeWithText("Galería").performClick()
            compose.waitUntil(20_000) { imported?.isFile == true }
            assertEquals("GALLERY", origin)
            val decoded = BitmapFactory.decodeFile(imported!!.path)
            assertEquals(640, decoded.width); assertEquals(480, decoded.height); decoded.recycle()
        } finally { instrumentation.removeMonitor(monitor); fixture.delete(); imported?.delete() }
    }
}
