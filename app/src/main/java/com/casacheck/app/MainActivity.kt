package com.casacheck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.casacheck.app.ui.HomeCheckApp
import com.casacheck.app.ui.account.RecoveryIntentState

class MainActivity : ComponentActivity() {
    private val recoveryState by lazy { ViewModelProvider(this)[RecoveryIntentState::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recoveryState.initialize(if (intent.action == Intent.ACTION_VIEW) intent.dataString else null)
        setContent {
            HomeCheckApp(incomingRecovery = recoveryState.incoming, onRecoveryHandled = {
                recoveryState.clear()
                intent.data = null
                intent.action = Intent.ACTION_MAIN
            })
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) recoveryState.receive(intent.dataString)
    }
}
