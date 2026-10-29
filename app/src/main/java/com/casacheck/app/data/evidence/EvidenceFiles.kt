package com.casacheck.app.data.evidence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun cameraFile(context: Context): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")

fun saveEvidence(context: Context, source: File, inspectionId: String, itemId: String, capturedAt: Long, origin: String = "CAMERA"): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "La cámara no produjo una imagen válida" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val original = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("No se pudo leer la fotografía")
    val exif = ExifInterface(source)
    val matrix = Matrix().apply {
        postRotate(exif.rotationDegrees.toFloat())
        if (exif.isFlipped) postScale(-1f, 1f)
    }
    val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    val bitmap = oriented.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bitmap)
    val size = (bitmap.width * .020f).coerceAtLeast(12f)
    val barHeight = size * 3
    canvas.drawRect(0f, bitmap.height - barHeight, bitmap.width.toFloat(), bitmap.height.toFloat(), Paint().apply { color = Color.argb(210, 8, 33, 58) })
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(capturedAt))
    canvas.drawText("HomeCheck · ${if (origin == "GALLERY") "Importada" else "Capturada"} · $stamp", size / 2, bitmap.height - size, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = size })
    val directory = File(context.filesDir, "evidence").apply { mkdirs() }
    val output = File(directory, "${inspectionId}_${itemId}_${UUID.randomUUID()}.jpg")
    try {
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
    } catch (e: Exception) { output.delete(); throw e }
    finally {
        bitmap.recycle()
        if (oriented !== original) oriented.recycle()
        original.recycle()
    }
    return output.absolutePath
}
