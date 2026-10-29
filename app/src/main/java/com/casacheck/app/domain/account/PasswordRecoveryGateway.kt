package com.casacheck.app.domain.account

class VerifiedPasswordReset(val email: String, val actionCode: String) {
    override fun toString() = "VerifiedPasswordReset(redacted)"
}

interface PasswordRecoveryGateway {
    suspend fun request(email: String)
    suspend fun verify(link: String): VerifiedPasswordReset
    suspend fun confirm(reset: VerifiedPasswordReset, password: String)
}
