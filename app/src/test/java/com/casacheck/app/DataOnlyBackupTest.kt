package com.casacheck.app

import com.casacheck.app.data.remote.dataOnlySnapshot
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaPhoto
import com.casacheck.app.domain.model.CasaRoom
import com.casacheck.app.domain.model.Condition

import org.junit.Assert.*
import org.junit.Test

class DataOnlyBackupTest {
    @Test fun backupExcludesLocalEvidenceAndApprovalWithoutChangingTheOriginal() {
        val item = CasaItem(name = "Pared", condition = Condition.DAMAGED, quantity = 2,
            notes = "Fisura", photoPath = "/private/photo.jpg", capturedAt = 123L,
            extraPhotos = listOf(CasaPhoto("/private/extra.jpg", 456L)))
        val original = CasaInspection(propertyName = "Casa", address = "Dirección", type = "Casa",
            rooms = listOf(CasaRoom(name = "Sala", items = listOf(item))), completed = true,
            ownerSignature = "1,2", renterSignature = "3,4", reportPath = "/private/report.pdf")
        val snapshot = original.dataOnlySnapshot()
        assertFalse(snapshot.completed)
        assertEquals("", snapshot.reportPath)
        assertEquals("", snapshot.ownerSignature)
        assertEquals("", snapshot.renterSignature)
        assertEquals(item.copy(photoPath = "", extraPhotos = emptyList()), snapshot.rooms.single().items.single())
        assertEquals(original.id, snapshot.id)
        assertEquals(original.propertyId, snapshot.propertyId)
        assertTrue(original.completed)
        assertEquals("/private/photo.jpg", original.rooms.single().items.single().photoPath)
        assertEquals(snapshot, snapshot.dataOnlySnapshot())
    }
}
