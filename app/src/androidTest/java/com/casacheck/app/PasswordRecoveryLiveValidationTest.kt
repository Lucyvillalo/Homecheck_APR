package com.casacheck.app

import androidx.test.platform.app.InstrumentationRegistry
import com.casacheck.app.data.remote.FirebaseServices
import com.casacheck.app.data.remote.FirebasePasswordRecoveryRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only check of the actual project's reset endpoint; never sends mail or changes a password. */
class PasswordRecoveryLiveValidationTest {
    @Test fun actualFirebaseResetEndpointRejectsAnInvalidCodeExplicitly() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseLive") == "true")
        assertTrue(FirebaseServices.initialize(InstrumentationRegistry.getInstrumentation().targetContext))
        val project = FirebaseApp.getInstance().options.projectId
        val link = "https://$project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=invalid-qa-reset-token-never-issued"
        withTimeout(40_000) {
            try { FirebasePasswordRecoveryRepository().verify(link); fail("Invalid test code was accepted") }
            catch (expected: FirebaseAuthException) { assertEquals("ERROR_INVALID_ACTION_CODE", expected.errorCode) }
        }
    }
}
