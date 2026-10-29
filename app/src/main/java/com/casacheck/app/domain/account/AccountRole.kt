package com.casacheck.app.domain.account

/** Product roles, never Firebase project administrator privileges. */
enum class AccountRole(val label: String, val workspaceTitle: String, val propertyTitle: String) {
    OWNER("Propietario", "Panel del propietario", "Mis propiedades"),
    TENANT("Inquilino", "Panel del inquilino", "Mis alquileres"),
    STAFF("Encargado", "Panel del encargado", "Espacios asignados");
    val managesRentals get() = this == OWNER
    val requestsRentals get() = this == TENANT
    val requiresAssignment get() = this == STAFF
    companion object {
        fun fromStored(value: String): AccountRole = entries.firstOrNull { it.label == value }
            ?: throw IllegalStateException("La cuenta no tiene un rol válido. Completa tu perfil antes de continuar.")
    }
}
