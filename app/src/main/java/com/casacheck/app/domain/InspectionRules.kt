package com.casacheck.app.domain

import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.Condition
import java.util.UUID

fun newAfter(before: CasaInspection) = before.copy(
    id = UUID.randomUUID().toString(), phase = "DESPUÉS", baselineId = before.id,
    createdAt = System.currentTimeMillis(), completed = false, ownerName = "", renterName = "",
    ownerSignature = "", renterSignature = "", reportPath = "",
    rooms = before.rooms.map { room -> room.copy(items = room.items.map { item ->
        item.copy(condition = Condition.PENDING, notes = "", photoPath = "", capturedAt = 0L, extraPhotos = emptyList(), photoOrigin = "CAMERA")
    }) }
)

fun missingRequirements(inspection: CasaInspection): List<String> = inspection.rooms.flatMap { room ->
    room.items.filter { item ->
        item.condition == Condition.PENDING || item.quantity !in 0..9999 ||
        (item.condition != Condition.MISSING && item.quantity == 0) ||
        (item.critical && item.photoPath.isBlank()) ||
        (item.condition == Condition.MISSING && item.quantity != 0) ||
        (item.condition != Condition.GOOD && item.condition != Condition.PENDING && item.notes.isBlank())
    }.map { "${room.name}: ${it.name}" }
}
