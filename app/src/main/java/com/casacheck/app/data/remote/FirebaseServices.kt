package com.casacheck.app.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.casacheck.app.domain.account.assessPassword
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Source
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

/** Configuration is a public Firebase Android client config, never an admin credential. */
object FirebaseServices {
    fun initialize(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        return runCatching {
            val j = JSONObject(context.assets.open("firebase-config.json").bufferedReader().use { it.readText() })
            FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApplicationId(j.getString("applicationId"))
                .setApiKey(j.getString("apiKey"))
                .setProjectId(j.getString("projectId")).build())
            true
        }.getOrDefault(false)
    }

    suspend fun register(name: String, email: String, password: String, role: String, acceptedPolicy: Boolean) {
        validateProfile(name, acceptedPolicy)
        if (!assessPassword(password).longEnough) throw AccountValidationException("PASSWORD_TOO_SHORT")
        val auth = FirebaseAuth.getInstance()
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).awaitOnline().user
            ?: error("No se pudo crear la cuenta")
        try { saveProfile(user, name, email, role) }
        catch (error: Exception) {
            auth.signOut()
            throw RegistrationIncompleteException(error)
        }
    }

    suspend fun completeRegistration(name: String, email: String, password: String, role: String, acceptedPolicy: Boolean) {
        validateProfile(name, acceptedPolicy)
        val auth = FirebaseAuth.getInstance()
        val user = auth.signInWithEmailAndPassword(email.trim(), password).awaitOnline().user
            ?: error("No se pudo iniciar sesión")
        try { saveProfile(user, name, email, role) }
        catch (error: Exception) {
            auth.signOut()
            throw RegistrationIncompleteException(error)
        }
    }

    suspend fun signIn(email: String, password: String) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.signInWithEmailAndPassword(email.trim(), password).awaitOnline().user
            ?: error("No se pudo iniciar sesión")
        try {
            val profile = FirebaseFirestore.getInstance().collection("usuarios").document(user.uid).get(Source.SERVER).awaitOnline()
            if (!profile.exists()) throw ProfileCompletionRequiredException(user.displayName.orEmpty())
        } catch (error: Exception) { auth.signOut(); throw error }
    }

    private fun validateProfile(name: String, acceptedPolicy: Boolean) {
        if (name.isBlank()) throw AccountValidationException("NAME_REQUIRED")
        if (!acceptedPolicy) throw AccountValidationException("POLICY_REQUIRED")
    }

    private suspend fun saveProfile(user: FirebaseUser, name: String, email: String, role: String) {
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).awaitOnline()
        val profile = FirebaseFirestore.getInstance().collection("usuarios").document(user.uid)
        // Preserve the role and registration date if a previous write succeeded before a timeout.
        if (!profile.get(Source.SERVER).awaitOnline().exists()) {
            profile.set(mapOf("idUsuario" to user.uid, "nombre" to name.trim(), "correo" to email.trim(), "rol" to role,
                "fechaRegistro" to FieldValue.serverTimestamp(), "policyVersion" to "2026-10",
                "policyAcceptedAt" to FieldValue.serverTimestamp())).awaitOnline()
        }
    }
}
