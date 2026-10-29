package com.casacheck.app.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.util.UUID

class IncomingRecoveryLink(val id: String, val url: String) {
    override fun toString() = "IncomingRecoveryLink(redacted)"
}

/** Retains an incoming link only in process memory across configuration changes. */
class RecoveryIntentState : ViewModel() {
    var incoming by mutableStateOf<IncomingRecoveryLink?>(null)
        private set
    private var initialized = false
    fun initialize(url: String?) { if (!initialized) { initialized = true; receive(url) } }
    fun receive(url: String?) { if (!url.isNullOrBlank()) incoming = IncomingRecoveryLink(UUID.randomUUID().toString(), url) }
    fun clear() { incoming = null }
}
