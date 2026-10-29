package com.casacheck.app

import com.casacheck.app.data.evidence.cameraFile
import com.casacheck.app.data.evidence.saveEvidence
import com.casacheck.app.data.local.CasaStore
import com.casacheck.app.data.report.createActaPdf
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaPhoto
import com.casacheck.app.domain.model.CasaRoom
import com.casacheck.app.domain.model.Condition
import com.casacheck.app.domain.newAfter
import com.casacheck.app.domain.roomsFor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InspectionIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun persistenceRoundTripAndAccountIsolation() {
        val inspection = CasaInspection(propertyName = "Prueba", address = "Dirección", type = "Casa", rooms = listOf(CasaRoom(name = "Sala", items = listOf(CasaItem(name = "Pared", photoPath = "primary", photoOrigin = "GALLERY", extraPhotos = listOf(CasaPhoto("extra", 1234L, "CAMERA")))))))
        try {
            CasaStore.save(context, listOf(inspection), "test-a")
            CasaStore.save(context, emptyList(), "test-b")
            assertEquals(inspection, CasaStore.load(context, "test-a").single())
            assertTrue(CasaStore.load(context, "test-b").isEmpty())
        } finally { CasaStore.save(context, emptyList(), "test-a"); CasaStore.save(context, emptyList(), "test-b") }
    }

    @Test fun evidenceAndComparativePdfRenderWithLongNotes() {
        val source = cameraFile(context)
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(245, 225, 210)) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        val start = System.nanoTime()
        val photo = saveEvidence(context, source, "integration", "wall", 1793152800000L)
        source.delete()
        val evidence = android.graphics.BitmapFactory.decodeFile(photo)
        assertEquals(1600, evidence.width)
        assertTrue(Color.blue(evidence.getPixel(1, evidence.height - 2)) < 150)
        evidence.recycle()
        val before = CasaInspection(id = "qa-before", propertyName = "Casa de verificación", address = "Dirección de prueba", type = "Casa", rooms = listOf(CasaRoom(name = "Habitación", items = listOf(CasaItem(name = "Paredes", condition = Condition.GOOD, critical = true, photoPath = photo)))))
        val after = newAfter(before).copy(id = "qa-comparative", rooms = before.rooms.map { it.copy(items = it.items.map { item -> item.copy(condition = Condition.DAMAGED, notes = "Observación extensa para comprobar que el acta conserva el texto completo. ".repeat(15), photoPath = photo) }) }, ownerName = "Responsable de prueba", renterName = "Inquilino de prueba", ownerSignature = "0.1,0.1;0.3,0.5;0.8,0.3", renterSignature = "0.1,0.7;0.5,0.2;0.8,0.5")
        val pdfStart = System.nanoTime()
        val pdf = createActaPdf(context, after, before)
        val duration = (System.nanoTime() - pdfStart) / 1_000_000
        assertTrue(pdf.length() > 1000)
        val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descriptor).use { renderer ->
            assertTrue(renderer.pageCount >= 2)
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    val rendered = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.filesDir, "qa-page-$index.png").outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    rendered.recycle()
                }
            }
        }
        android.util.Log.i("HomeCheckQA", "PDF generation: $duration ms; total evidence and report: ${(System.nanoTime()-start)/1_000_000} ms")
        File(context.filesDir, "qa-timing.txt").writeText("PDF generation: $duration ms")
    }

    @Test fun residentialReportWithPhotographsInEveryItemIsGeneratedWithinFiveSeconds() {
        val image = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 232, 245)) }
        val file = File(context.cacheDir, "benchmark-evidence.jpg")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 85, it) }; image.recycle()
        val inspection = CasaInspection(propertyName = "Benchmark residencial", address = "Dirección de prueba", type = "Casa",
            rooms = roomsFor("Casa").map { room -> room.copy(items = room.items.map { it.copy(condition = Condition.GOOD, photoPath = file.path) }) },
            ownerName = "Propietario QA", renterName = "Inquilino QA", ownerSignature = "0.1,0.1;0.8,0.6", renterSignature = "0.1,0.6;0.8,0.1")
        val started = System.nanoTime()
        val pdf = createActaPdf(context, inspection, null)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        File(context.filesDir, "qa-residential-timing.txt").writeText("${inspection.rooms.sumOf { it.items.size }} items with photographs; PDF generation: $elapsed ms; bytes: ${pdf.length()}")
        assertTrue("Residential PDF took $elapsed ms", elapsed < 5000)
        assertTrue(pdf.length() > 1000)
        file.delete(); pdf.delete()
    }
}
