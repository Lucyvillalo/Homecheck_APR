package com.casacheck.app.data.remote

import com.casacheck.app.domain.model.CasaInspection

/** Removes device-only evidence and approval before transmitting or restoring a record. */
internal fun CasaInspection.dataOnlySnapshot(): CasaInspection = copy(
    rooms = rooms.map { room -> room.copy(items = room.items.map { item ->
        item.copy(photoPath = "", extraPhotos = emptyList())
    }) },
    ownerSignature = "", renterSignature = "", reportPath = "", completed = false
)
