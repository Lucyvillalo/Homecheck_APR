package com.casacheck.app.data.remote

import com.casacheck.app.domain.account.PasswordRecoveryGateway
import com.casacheck.app.domain.account.VerifiedPasswordReset
import com.casacheck.app.domain.account.assessPassword
import com.casacheck.app.domain.account.resetCodeFromLink
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuthException

class FirebasePasswordRecoveryRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) : PasswordRecoveryGateway {
    override suspend fun request(email: String) {
        auth.setLanguageCode("es")
        val projectId = checkNotNull(auth.app.options.projectId)
        val settings = ActionCodeSettings.newBuilder()
            .setUrl("https://$projectId.firebaseapp.com/")
            .setAndroidPackageName("com.casacheck.app", false, null)
            .setHandleCodeInApp(true)
            .build()
        try { auth.sendPasswordResetEmail(email.trim(), settings).awaitOnline() }
        catch (error: FirebaseAuthException) {
            // Do not disclose whether a submitted email has an account.
            if (error.errorCode != "ERROR_USER_NOT_FOUND") throw error
        }
    }

    override suspend fun verify(link: String): VerifiedPasswordReset {
        val projectId = checkNotNull(auth.app.options.projectId)
        val code = resetCodeFromLink(link, projectId)
        val email = auth.verifyPasswordResetCode(code).awaitOnline()
        return VerifiedPasswordReset(email, code)
    }

    override suspend fun confirm(reset: VerifiedPasswordReset, password: String) {
        if (!assessPassword(password).longEnough) throw AccountValidationException("RESET_PASSWORD_TOO_SHORT")
        auth.confirmPasswordReset(reset.actionCode, password).awaitOnline()
        auth.signOut()
    }
}
