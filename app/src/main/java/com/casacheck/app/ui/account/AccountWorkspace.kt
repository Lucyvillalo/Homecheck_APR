package com.casacheck.app.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.casacheck.app.data.remote.PropertyRepository
import com.casacheck.app.domain.account.AccountRole
import com.casacheck.app.ui.CasaCheckApp
import kotlinx.coroutines.CancellationException

/** An existing session never obtains a role from the login portal's visual selection. */
@Composable
internal fun AccountWorkspace(account: String, onLogout: () -> Unit) {
    var role by remember(account) { mutableStateOf<AccountRole?>(null) }
    var error by remember(account) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(account,attempt) {
        error = false
        try { role = AccountRole.fromStored(PropertyRepository().role(allowCache = true)) }
        catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = true }
    }
    val verifiedRole = role
    if (verifiedRole != null) CasaCheckApp(account,verifiedRole,onLogout)
    else Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement = Arrangement.Center,horizontalAlignment = Alignment.CenterHorizontally) {
        if (!error) { CircularProgressIndicator(); Text("Comprobando el perfil de tu cuenta…") }
        else {
            Text("No se pudo comprobar tu perfil. Revisa Internet o vuelve a iniciar sesión.")
            Button(onClick = { attempt++ }) { Text("Reintentar") }
            TextButton(onClick = onLogout) { Text("Volver al acceso") }
        }
    }
}
