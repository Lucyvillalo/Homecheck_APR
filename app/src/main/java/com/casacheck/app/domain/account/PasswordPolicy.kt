package com.casacheck.app.domain.account

/** Local guidance, not an entropy estimate or proof that a password is safe. */
data class PasswordAssessment(val level: Int, val label: String, val longEnough: Boolean,
    val lowercase: Boolean, val uppercase: Boolean, val number: Boolean, val symbol: Boolean)

fun assessPassword(password: String): PasswordAssessment {
    val lower = password.any(Char::isLowerCase)
    val upper = password.any(Char::isUpperCase)
    val number = password.any(Char::isDigit)
    val symbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    val variety = listOf(lower, upper, number, symbol).count { it }
    val common = password.lowercase() in setOf("password", "password123", "12345678", "123456789", "qwerty123", "contraseña", "contraseña123") ||
        (password.isNotEmpty() && password.toSet().size <= 2)
    val level = when {
        password.isEmpty() -> 0
        password.length < 8 || password.isBlank() || common -> 1
        password.length >= 12 && variety >= 3 || password.length >= 16 && variety >= 2 -> 3
        variety >= 3 || password.length >= 12 -> 2
        else -> 1
    }
    return PasswordAssessment(level, listOf("Sin evaluar", "Débil", "Media", "Fuerte")[level],
        password.length >= 8 && password.isNotBlank(), lower, upper, number, symbol)
}
