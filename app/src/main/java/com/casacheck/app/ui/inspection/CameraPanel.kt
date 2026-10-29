package com.casacheck.app.ui.inspection

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.casacheck.app.data.evidence.cameraFile
import com.casacheck.app.ui.components.HomeCheckIcon
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CameraPanel(busy: Boolean, onPhoto: (File, String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val preview = remember { Preview.Builder().build() }
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var ready by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it; if (!it) error = "Permite el acceso a la cámara en los ajustes de la aplicación o utiliza Galería." }
    val latestPhoto by rememberUpdatedState(onPhoto)
    DisposableEffect(lifecycle, permission) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        if (permission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) try {
                    provider = future.get()
                    preview.setSurfaceProvider { request = it }
                    val camera = provider!!.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    hasFlash = camera.cameraInfo.hasFlashUnit()
                    ready = true; error = ""
                } catch (e: Exception) { error = "No se pudo iniciar la cámara. Puedes incorporar una fotografía desde Galería."; ready = false }
            }, executor)
        }
        onDispose { disposed = true; ready = false; provider?.unbind(preview, capture) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            capturing = true; error = ""
            scope.launch {
                var file: File? = null
                try {
                    file = withContext(Dispatchers.IO) {
                        val output = cameraFile(context)
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input -> output.outputStream().use { out ->
                                val buffer = ByteArray(8192); var total = 0; var count = input.read(buffer)
                                while (count != -1) { total += count; check(total <= 25 * 1024 * 1024) { "La imagen supera 25 MB" }; out.write(buffer, 0, count); count = input.read(buffer) }
                            } } ?: error("No se pudo leer la imagen seleccionada")
                            output
                        } catch (e: Exception) { output.delete(); throw e }
                    }
                    latestPhoto(file, "GALLERY")
                } catch (e: Exception) { file?.delete(); error = "No se pudo importar la imagen. Selecciona una fotografía de hasta 25 MB." }
                finally { capturing = false }
            }
        }
    }
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
          val previewHeight = (maxWidth * 3f / 4f).coerceIn(220.dp, 360.dp)
          Box(Modifier.fillMaxWidth().height(previewHeight).clipToBounds().testTag("camera-preview").background(Color(0xFF15212D)), contentAlignment = Alignment.Center) {
            val surface = request
            if (permission && surface != null) CameraXViewfinder(surfaceRequest = surface, modifier = Modifier.fillMaxSize().clipToBounds())
            else if (!permission) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Vista previa de cámara", color = Color.White)
                TextButton(onClick = { grant.launch(Manifest.permission.CAMERA) }, enabled = !busy) { Text("Activar cámara", color = Color.White) }
            } else CircularProgressIndicator(color = Color.White)
          }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { flash = !flash; capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }, enabled = ready && hasFlash && !busy && !capturing) { Text(if (flash) "Flash: sí" else "Flash: no") }
            FilledIconButton(onClick = {
                val file = cameraFile(context)
                capturing = true; error = ""
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) { capturing = false; latestPhoto(file, "CAMERA") }
                    override fun onError(exception: ImageCaptureException) { file.delete(); capturing = false; error = "No se pudo capturar la fotografía. Intenta nuevamente." }
                })
            }, enabled = ready && !busy && !capturing, modifier = Modifier.size(64.dp).testTag("capture-photo"), shape = CircleShape) { HomeCheckIcon("camera", "Tomar fotografía", Modifier.size(30.dp), Color.White) }
            TextButton(onClick = { gallery.launch("image/*") }, enabled = !busy && !capturing) { Text("Galería") }
        }
        if (capturing || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
