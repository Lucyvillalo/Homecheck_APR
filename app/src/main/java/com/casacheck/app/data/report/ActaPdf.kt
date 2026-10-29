package com.casacheck.app.data.report

import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.casacheck.app.core.formatDate
import com.casacheck.app.core.parseSignature
import com.casacheck.app.domain.missingRequirements
import com.casacheck.app.domain.model.CasaInspection
import java.io.File

fun createActaPdf(context: android.content.Context, i: CasaInspection, before: CasaInspection?): File {
    check(missingRequirements(i).isEmpty()) { "Inspección incompleta" }
    val doc = PdfDocument()
    try {
    val width = 595
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(width, 842, pageNo).create())
    var canvas = page.canvas
    var y = 52f
    val title = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(18, 59, 102); textSize = 20f; isFakeBoldText = true }
    val body = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35, 48, 62); textSize = 10.5f }
    val head = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(18, 59, 102); textSize = 13f; isFakeBoldText = true }
    fun nextPage() { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(width, 842, pageNo).create()); canvas = page.canvas; y = 48f }
    fun line(value: String, paint: NativePaint = body, left: Float = 36f) {
        if (y > 775f) nextPage()
        val available = width - left - 36f
        for (paragraph in value.split('\n')) {
            var remaining = paragraph
            do {
                val measured = paint.breakText(remaining, true, available, null).coerceAtLeast(1)
                val end = if (measured < remaining.length) remaining.lastIndexOf(' ', measured).takeIf { it > 0 } ?: measured else remaining.length
                if (y > 775f) nextPage()
                canvas.drawText(remaining.take(end), left, y, paint)
                y += if (paint === head) 21f else 16f
                remaining = remaining.drop(end).trimStart()
            } while (remaining.isNotEmpty())
        }
    }
    canvas.drawText("ACTA DIGITAL DE ESTADO", 36f, y, title); y += 30f
    line(i.propertyName, head); line("${i.type} · ${i.phase} · ${formatDate(i.createdAt)}"); line(i.address)
    if (before != null) line("Comparación vinculada: Antes · ${formatDate(before.createdAt)}")
    y += 8f
    i.rooms.forEach { r ->
        line(r.name, head)
        r.items.forEach { x ->
            val info = "${x.name}: ${x.condition.label} · Cantidad ${x.quantity}" + if (x.notes.isBlank()) "" else " · ${x.notes}"
            line(info, body, 44f)
            val old = before?.rooms?.firstOrNull { it.id == r.id }?.items?.firstOrNull { it.id == x.id }
            if (old != null) line("Antes: ${old.condition.label} · Cantidad ${old.quantity}", body, 44f)
            if (old != null && (old.condition != x.condition || old.quantity != x.quantity)) line("CAMBIO DE ESTADO O CANTIDAD", head, 44f)
            fun photoLabel(label: String, origin: String) = label + if (origin == "GALLERY") " · importada" else " · cámara"
            val evidence = if (old != null) listOf(photoLabel("Antes", old.photoOrigin) to old.photoPath, photoLabel("Después", x.photoOrigin) to x.photoPath) else listOf(photoLabel("Evidencia", x.photoOrigin) to x.photoPath)
            val additional = (old?.extraPhotos.orEmpty().map { photoLabel("Antes · adicional", it.origin) to it.path } + x.extraPhotos.map { photoLabel(if (before == null) "Adicional" else "Después · adicional", it.origin) to it.path })
            (listOf(evidence) + additional.chunked(2)).forEach { evidence ->
            if (evidence.any { it.second.isNotBlank() }) {
                if (y + 170f > 775f) nextPage()
                val top = y
                evidence.forEachIndexed { index, (label, path) ->
                    val left = 44f + index * 255f
                    canvas.drawText(label, left, top, body)
                    if (path.isNotBlank()) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                        val photo = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                            ?: error("No se pudo leer la evidencia de ${x.name}")
                        val scale = minOf(240f / photo.width, 125f / photo.height)
                        canvas.drawBitmap(photo, null, RectF(left, top + 10f, left + photo.width * scale, top + 10f + photo.height * scale), null)
                        photo.recycle()
                    }
                }
                y = top + 155f
            }
            }
        }
    }
    nextPage()
    canvas.drawText("FIRMAS Y ACEPTACIÓN", 36f, y, title); y += 35f
    line("Propietario / encargado: ${i.ownerName}", head)
    drawSignature(canvas, i.ownerSignature, 42f, y, 220f, 85f); y += 105f
    line("Inquilino / responsable: ${i.renterName}", head)
    drawSignature(canvas, i.renterSignature, 42f, y, 220f, 85f); y += 110f
    line("Ambas partes aceptaron el estado documentado.")
    line("Generada por HomeCheck · ${formatDate(System.currentTimeMillis())}")
    doc.finishPage(page)
    val dir = File(context.filesDir, "reports").apply { mkdirs() }
    val output = File(dir, "${i.id}.pdf")
    output.outputStream().use { doc.writeTo(it) }
    return output
    } finally { doc.close() }
}

private fun drawSignature(canvas: NativeCanvas, encoded: String, left: Float, top: Float, width: Float, height: Float) {
    val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(18, 59, 102); style = NativePaint.Style.STROKE; strokeWidth = 2f }
    parseSignature(encoded).forEach { points ->
        if (points.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(left + points.first().x * width, top + points.first().y * height)
            points.drop(1).forEach { path.lineTo(left + it.x * width, top + it.y * height) }
            canvas.drawPath(path, paint)
        }
    }
    canvas.drawLine(left, top + height, left + width, top + height, paint)
}
