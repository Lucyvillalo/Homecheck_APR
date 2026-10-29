package com.casacheck.app.domain.model

import java.util.UUID

enum class Condition(val label: String) {
    PENDING("Sin revisar"), GOOD("Bueno"), REGULAR("Regular"),
    STAINED("Manchado"), DAMAGED("Dañado"), MISSING("Faltante")
}

data class CasaPhoto(val path: String, val capturedAt: Long, val origin: String = "CAMERA")

data class CasaItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val critical: Boolean = false,
    val condition: Condition = Condition.PENDING,
    val quantity: Int = 1,
    val notes: String = "",
    val photoPath: String = "",
    val capturedAt: Long = 0L,
    val extraPhotos: List<CasaPhoto> = emptyList(),
    val photoOrigin: String = "CAMERA"
)

data class CasaRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<CasaItem>
)

data class CasaInspection(
    val id: String = UUID.randomUUID().toString(),
    val propertyId: String = UUID.randomUUID().toString(),
    val propertyName: String,
    val address: String,
    val type: String,
    val phase: String = "ANTES",
    val baselineId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val rooms: List<CasaRoom>,
    val ownerName: String = "",
    val renterName: String = "",
    val ownerSignature: String = "",
    val renterSignature: String = "",
    val reportPath: String = ""
)
