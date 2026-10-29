package com.casacheck.app

import com.casacheck.app.domain.missingRequirements
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaRoom
import com.casacheck.app.domain.model.Condition
import com.casacheck.app.domain.newAfter

import org.junit.Assert.*
import org.junit.Test

class InspectionRulesTest {
    private fun inspection(item: CasaItem) = CasaInspection(propertyName = "Casa", address = "Dirección", type = "Casa", rooms = listOf(CasaRoom(name = "Sala", items = listOf(item))))
    @Test fun afterPreservesInventoryAndIdentifiersButClearsEvidenceAndSignatures() {
        val before = inspection(CasaItem(name = "Sillas", quantity = 45, condition = Condition.GOOD, photoPath = "photo", capturedAt = 1)).copy(completed = true, ownerSignature = "signature", reportPath = "report")
        val after = newAfter(before)
        assertEquals(before.id, after.baselineId)
        assertEquals(before.propertyId, after.propertyId)
        assertEquals(before.rooms.first().items.first().id, after.rooms.first().items.first().id)
        assertEquals(45, after.rooms.first().items.first().quantity)
        assertEquals(Condition.PENDING, after.rooms.first().items.first().condition)
        assertEquals("", after.rooms.first().items.first().photoPath)
        assertEquals("", after.ownerSignature)
        assertFalse(after.completed)
    }
    @Test fun rejectsMissingPhotoAndUnreviewedItems() {
        assertTrue(missingRequirements(inspection(CasaItem(name = "Piso", critical = true, condition = Condition.GOOD))).isNotEmpty())
        assertTrue(missingRequirements(inspection(CasaItem(name = "Piso"))).isNotEmpty())
    }
    @Test fun requiresZeroForMissingAndPositiveForPresentInventory() {
        assertTrue(missingRequirements(inspection(CasaItem(name = "Silla", condition = Condition.GOOD, quantity = 0))).isNotEmpty())
        assertTrue(missingRequirements(inspection(CasaItem(name = "Silla", condition = Condition.MISSING, quantity = 1, notes = "No está"))).isNotEmpty())
        assertTrue(missingRequirements(inspection(CasaItem(name = "Silla", condition = Condition.MISSING, quantity = 0, notes = "No está"))).isEmpty())
    }
    @Test fun requiresNotesForDamageAndAcceptsCompleteEvidence() {
        assertTrue(missingRequirements(inspection(CasaItem(name = "Piso", condition = Condition.DAMAGED))).isNotEmpty())
        assertTrue(missingRequirements(inspection(CasaItem(name = "Piso", critical = true, condition = Condition.GOOD, photoPath = "photo"))).isEmpty())
    }
}
