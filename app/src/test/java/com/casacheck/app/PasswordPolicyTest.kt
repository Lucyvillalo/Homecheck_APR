package com.casacheck.app

import com.casacheck.app.domain.account.assessPassword
import com.casacheck.app.domain.account.accountIssueFor
import org.junit.Assert.*
import org.junit.Test

class PasswordPolicyTest {
    @Test fun shortAndCommonPasswordsAreNotPresentedAsStrong() {
        assertFalse(assessPassword("Ab1!").longEnough)
        assertEquals("Débil", assessPassword("password123").label)
        assertEquals("Débil", assessPassword("aaaaaaaaaaaa").label)
        assertEquals("Sin evaluar", assessPassword("").label)
    }
    @Test fun longerMixedPasswordsHaveClearLevels() {
        assertEquals("Media", assessPassword("Casa123!").label)
        val strong = assessPassword("MiClaveLarga!42")
        assertTrue(strong.longEnough && strong.lowercase && strong.uppercase && strong.number && strong.symbol)
        assertEquals("Fuerte", strong.label)
        assertFalse(assessPassword("        ").longEnough)
    }
    @Test fun accountErrorsExplainTheCorrectRecovery() {
        assertTrue(accountIssueFor("ERROR_EMAIL_ALREADY_IN_USE").message.contains("ya está registrado"))
        assertTrue(accountIssueFor("FIRESTORE_PERMISSION_DENIED").message.contains("No es un error de tu contraseña"))
        assertTrue(accountIssueFor("NETWORK_ERROR").message.contains("Internet"))
        assertTrue(accountIssueFor("PROFILE_REQUIRED").message.contains("misma contraseña"))
        assertEquals(accountIssueFor("ERROR_WRONG_PASSWORD").message, accountIssueFor("ERROR_USER_NOT_FOUND").message)
    }
}
