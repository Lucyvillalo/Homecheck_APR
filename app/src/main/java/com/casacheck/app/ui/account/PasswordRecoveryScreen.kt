package com.casacheck.app.ui.account

import android.content.Intent
import android.os.SystemClock
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casacheck.app.R
import com.casacheck.app.data.remote.FirebasePasswordRecoveryRepository
import com.casacheck.app.data.remote.describeAccountFailure
import com.casacheck.app.domain.account.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RecoveryStep { REQUEST, SENT, VERIFYING, LINK_ERROR, PASSWORD, DONE }

@Composable
internal fun PasswordRecoveryScreen(configured: Boolean, initialEmail: String,
    onBack: (String) -> Unit, onResetComplete: (String) -> Unit, gateway: PasswordRecoveryGateway? = null, initialLink: String? = null) {
    val context = LocalContext.current
    val service = remember(configured, gateway) { gateway ?: if (configured) FirebasePasswordRecoveryRepository() else null }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(RecoveryStep.REQUEST) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var completedEmail by rememberSaveable { mutableStateOf("") }
    var resendAt by rememberSaveable { mutableLongStateOf(0L) }
    var remaining by remember { mutableIntStateOf(0) }
    // One-time codes and passwords stay in memory only, never in saved instance state or logs.
    var reset by remember { mutableStateOf<VerifiedPasswordReset?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<AccountIssue?>(null) }
    var emailHelp by remember { mutableStateOf("") }
    val strength = assessPassword(password)
    var automaticLinkHandled by rememberSaveable(initialLink) { mutableStateOf(false) }
    val visibleStep = if (step == RecoveryStep.PASSWORD && reset == null) RecoveryStep.VERIFYING else step
    LaunchedEffect(resendAt) {
        do {
            remaining = ((resendAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999).div(1000).toInt()
            if (remaining > 0) delay(1000)
        } while (remaining > 0)
    }
    fun operate(block: suspend () -> Unit) {
        busy = true; issue = null; emailHelp = ""
        scope.launch {
            try { block() }
            catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                issue = describeAccountFailure(error)
                if (issue?.code in setOf("ERROR_EXPIRED_ACTION_CODE", "ERROR_INVALID_ACTION_CODE")) {
                    reset = null; password = ""; confirmation = ""; step = RecoveryStep.LINK_ERROR
                }
                else if (step == RecoveryStep.VERIFYING) step = RecoveryStep.LINK_ERROR
            } finally { busy = false }
        }
    }
    fun request() = operate {
        checkNotNull(service).request(email.trim())
        email = email.trim(); resendAt = SystemClock.elapsedRealtime() + 60_000; step = RecoveryStep.SENT
    }
    fun back() {
        if (busy) return
        issue = null
        when (visibleStep) {
            RecoveryStep.REQUEST -> onBack(email.trim())
            RecoveryStep.SENT -> onBack(email.trim())
            RecoveryStep.VERIFYING, RecoveryStep.LINK_ERROR -> onBack(email.trim())
            RecoveryStep.PASSWORD -> { reset = null; password = ""; confirmation = ""; onBack(email.trim()) }
            RecoveryStep.DONE -> onResetComplete(completedEmail)
        }
    }
    LaunchedEffect(initialLink) {
        if (initialLink != null && step != RecoveryStep.DONE && (!automaticLinkHandled || (step == RecoveryStep.PASSWORD && reset == null))) {
            automaticLinkHandled = true
            step = RecoveryStep.VERIFYING
            operate {
                reset = checkNotNull(service).verify(initialLink)
                email = reset!!.email
                step = RecoveryStep.PASSWORD
            }
        }
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = { back() }, enabled = !busy) { Text("‹ Volver") }
        Image(painterResource(R.drawable.homecheck_logo), "HomeCheck", Modifier.width(180.dp).height(72.dp))
        Spacer(Modifier.height(8.dp))
        Text(when (visibleStep) {
            RecoveryStep.REQUEST -> "Recuperar contraseña"
            RecoveryStep.SENT -> "Revisa tu correo"
            RecoveryStep.VERIFYING -> "Comprobando enlace"
            RecoveryStep.LINK_ERROR -> "No se pudo abrir el enlace"
            RecoveryStep.PASSWORD -> "Crear nueva contraseña"
            RecoveryStep.DONE -> "Contraseña actualizada"
        }, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (!configured) Text("La recuperación no está disponible: falta configurar Firebase. Contacta al equipo responsable.", color = MaterialTheme.colorScheme.error)
        when (visibleStep) {
            RecoveryStep.REQUEST -> {
                Text("Escribe el correo de tu cuenta. Te enviaremos un enlace seguro; no necesitas recordar la contraseña anterior.")
                OutlinedTextField(email, { email = it; issue = null }, label = { Text("Correo de la cuenta") },
                    isError = email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches(),
                    supportingText = { Text("Usa el mismo correo con el que te registraste.") },
                    singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                RecoveryError(issue)
                Button(shape = RoundedCornerShape(8.dp), onClick = { request() }, enabled = configured && !busy && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches(),
                    modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (busy) "Enviando solicitud…" else "Enviar enlace de recuperación") }
                TextButton(onClick = { onBack(email.trim()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Volver a iniciar sesión") }
            }
            RecoveryStep.SENT -> {
                Text("Correo: ${email.chunked(16).joinToString("\u200B")}", modifier = Modifier.fillMaxWidth(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Si este correo tiene una cuenta, recibirás un enlace para cambiar la contraseña.")
                Text("Revisa Entrada, Spam y Correo no deseado. La solicitud fue aceptada, pero aún debes comprobar que el mensaje llegó.", fontSize = 13.sp)
                Button(shape = RoundedCornerShape(8.dp), onClick = {
                    runCatching { context.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)) }
                        .onFailure { emailHelp = "No se encontró una app de correo. Abre tu correo desde el navegador y busca el mensaje de recuperación de Firebase." }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Abrir mi correo") }
                if (emailHelp.isNotBlank()) Text(emailHelp)
                Text("Abre el mensaje y pulsa el enlace más reciente. Android abrirá HomeCheck para que elijas la contraseña nueva.", fontSize = 13.sp)
                RecoveryError(issue)
                TextButton(onClick = { request() }, enabled = configured && !busy && remaining == 0,
                    modifier = Modifier.fillMaxWidth().testTag("recovery-resend")) { Text(if (busy) "Enviando…" else if (remaining > 0) "Reenviar en $remaining s" else "Reenviar enlace") }
                TextButton(onClick = { issue = null; step = RecoveryStep.REQUEST }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Corregir correo") }
                TextButton(onClick = { onBack(email.trim()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Volver a iniciar sesión") }
            }
            RecoveryStep.VERIFYING -> {
                Text("Estamos validando el enlace seguro de Firebase. No necesitas copiarlo ni pegarlo.")
            }
            RecoveryStep.LINK_ERROR -> {
                RecoveryError(issue)
                Button(shape = RoundedCornerShape(8.dp), onClick = {
                    step = RecoveryStep.VERIFYING
                    operate { reset = checkNotNull(service).verify(checkNotNull(initialLink)); email = reset!!.email; step = RecoveryStep.PASSWORD }
                }, enabled = configured && !busy, modifier = Modifier.fillMaxWidth()) { Text("Volver a comprobar") }
                OutlinedButton(onClick = { issue = null; step = RecoveryStep.REQUEST }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Solicitar otro enlace") }
            }
            RecoveryStep.PASSWORD -> {
                val target = reset
                Text("Para: ${target?.email?.substringBefore('@')?.take(1)}•••@${target?.email?.substringAfter('@')}")
                Text("Elige una contraseña nueva de al menos 8 caracteres. Debe ser distinta de las que usas en otros servicios.")
                OutlinedTextField(password, { password = it; issue = null }, label = { Text("Nueva contraseña") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Ocultar" else "Mostrar") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy, modifier = Modifier.fillMaxWidth())
                PasswordStrengthIndicator(strength)
                OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmar nueva contraseña") }, singleLine = true,
                    visualTransformation = if (showConfirmation) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showConfirmation = !showConfirmation }) { Text(if (showConfirmation) "Ocultar" else "Mostrar") } },
                    isError = confirmation.isNotBlank() && confirmation != password,
                    supportingText = { if (confirmation.isNotBlank() && confirmation != password) Text("Las contraseñas no coinciden.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy, modifier = Modifier.fillMaxWidth())
                RecoveryError(issue)
                Button(shape = RoundedCornerShape(8.dp), onClick = { operate {
                    val verified = checkNotNull(reset)
                    checkNotNull(service).confirm(verified, password)
                    completedEmail = verified.email; reset = null; password = ""; confirmation = ""; step = RecoveryStep.DONE
                } }, enabled = configured && !busy && target != null && strength.longEnough && password == confirmation,
                    modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (busy) "Guardando contraseña…" else "Guardar nueva contraseña") }
                TextButton(onClick = { reset = null; password = ""; confirmation = ""; issue = null; step = RecoveryStep.REQUEST }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Solicitar otro enlace") }
            }
            RecoveryStep.DONE -> {
                Text("Tu contraseña se actualizó correctamente. El enlace ya no se puede reutilizar.")
                Text("Inicia sesión con tu correo y la nueva contraseña. La anterior ya no te permitirá entrar.")
                Button(shape = RoundedCornerShape(8.dp), onClick = { onResetComplete(completedEmail) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Volver a iniciar sesión") }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun RecoveryError(issue: AccountIssue?) {
    if (issue != null) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(issue.message, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Referencia: ${issue.code}", fontSize = 11.sp)
        }
    }
}
