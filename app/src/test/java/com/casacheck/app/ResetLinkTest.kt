package com.casacheck.app

import com.casacheck.app.domain.account.*
import org.junit.Assert.*
import org.junit.Test

class ResetLinkTest {
    private val project = "demo-homecheck-reset"
    private val code = "one-time-test-code-123"
    @Test fun acceptsOnlyTheConfiguredProjectAndPasswordResetAction() {
        assertEquals(code, resetCodeFromLink("https://$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=$code&lang=es", project))
        assertEquals(code, resetCodeFromLink("https://$project.web.app/__/auth/action?oobCode=$code&mode=resetPassword", project))
    }
    @Test fun rejectsUntrustedAmbiguousOrIncompleteLinks() {
        val bad = listOf("http://$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=$code",
            "https://evil.example/__/auth/action?mode=resetPassword&oobCode=$code",
            "https://$project.firebaseapp.com.evil.example/__/auth/action?mode=resetPassword&oobCode=$code",
            "https://user@$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=$code",
            "https://$project.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=$code",
            "https://$project.firebaseapp.com/__/auth/action?mode=resetPassword",
            "https://$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=$code&oobCode=another-code",
            "not a link")
        bad.forEach { value ->
            try { resetCodeFromLink(value, project); fail("Unsafe link accepted") }
            catch (expected: InvalidResetLinkException) { /* Error does not echo the secret URL. */ }
        }
    }
    @Test fun resetErrorsExplainExpirationAndPreviouslyUsedLinks() {
        assertTrue(accountIssueFor("ERROR_EXPIRED_ACTION_CODE").message.contains("caducó"))
        assertTrue(accountIssueFor("ERROR_INVALID_ACTION_CODE").message.contains("ya se utilizó"))
        assertFalse(VerifiedPasswordReset("qa@example.com", "secret-code").toString().contains("secret-code"))
    }
    @Test fun acceptsCurrentHostingMobileLinksAndRejectsCrossProjectWrappers() {
        val direct = "https://$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=$code"
        val encoded = java.net.URLEncoder.encode(direct, "UTF-8")
        assertEquals(code, resetCodeFromLink("https://$project.firebaseapp.com/__/auth/links?link=$encoded&apn=com.casacheck.app", project))
        assertEquals(code, resetCodeFromLink("https://$project.firebaseapp.com/__/auth/links?deep_link_id=$encoded", project))
        val foreign = java.net.URLEncoder.encode("https://another-project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=$code", "UTF-8")
        try { resetCodeFromLink("https://$project.firebaseapp.com/__/auth/links?link=$foreign", project); fail("Foreign project accepted") }
        catch (expected: InvalidResetLinkException) { }
    }

}
