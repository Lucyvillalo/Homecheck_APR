package com.casacheck.app.data.remote

import com.casacheck.app.domain.account.InvalidResetLinkException
import com.casacheck.app.domain.account.AccountIssue
import com.casacheck.app.domain.account.accountIssueFor
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException

class RegistrationIncompleteException(cause: Throwable) : Exception("Account created; profile incomplete", cause)
class ProfileCompletionRequiredException(val displayName: String) : Exception("Profile required")
class AccountValidationException(val code: String) : IllegalArgumentException(code)

fun describeAccountFailure(error: Throwable): AccountIssue {
    if (error is RegistrationIncompleteException) {
        val detail = describeAccountFailure(error.cause ?: error)
        return AccountIssue("PROFILE_SAVE_${detail.code}", "Tu cuenta se creó, pero no se pudo completar el perfil. ${detail.message} Conserva la misma contraseña y pulsa «Completar registro» para reintentar sin crear otra cuenta.")
    }
    val code = when (error) {
        is InvalidResetLinkException -> "INVALID_RESET_LINK"
        is ProfileCompletionRequiredException -> "PROFILE_REQUIRED"
        is AccountValidationException -> error.code
        is TimeoutCancellationException -> "TIMEOUT"
        is FirebaseNetworkException -> "NETWORK_ERROR"
        is FirebaseTooManyRequestsException -> "ERROR_TOO_MANY_REQUESTS"
        is FirebaseAuthException -> error.errorCode
        is FirebaseFirestoreException -> "FIRESTORE_${error.code.name}"
        else -> "UNEXPECTED_ERROR"
    }
    return accountIssueFor(code)
}
