package com.casacheck.app.ui.account

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casacheck.app.R
import com.casacheck.app.data.remote.FirebaseServices
import com.casacheck.app.data.remote.ProfileCompletionRequiredException
import com.casacheck.app.data.remote.RegistrationIncompleteException
import com.casacheck.app.data.remote.describeAccountFailure
import com.casacheck.app.domain.account.assessPassword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun AccountScreen(configured: Boolean, onAuthenticated: () -> Unit, initialRole: String = "Inquilino", initialEmail: String = "") {
    var recovering by rememberSaveable { mutableStateOf(false) }
    var register by rememberSaveable { mutableStateOf(false) }
    var completingProfile by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf(initialRole) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var policy by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var showReference by remember { mutableStateOf(false) }
    var successNotice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val strength = assessPassword(password)
    val requirements = buildList {
        if (register && name.isBlank()) add("escribe tu nombre")
        if (!emailValid) add("escribe un correo válido")
        if (password.isBlank()) add("escribe tu contraseña")
        else if (register && !completingProfile && !strength.longEnough) add("usa al menos 8 caracteres")
        if (register && (confirmation.isBlank() || password != confirmation)) add("confirma la misma contraseña")
        if (register && !accepted) add("acepta las condiciones y la privacidad")
    }
    fun reportFailure(error: Exception) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        if (error is RegistrationIncompleteException) completingProfile = true
        if (error is ProfileCompletionRequiredException) {
            register = true; completingProfile = true
            if (name.isBlank()) name = error.displayName
        }
        val issue = describeAccountFailure(error)
        message = issue.message; reference = issue.code; successNotice = false; showReference = false
        // Only a safe error code; never log emails, passwords, tokens or server response bodies.
        Log.w("HomeCheckAuth", "Account operation failed: ${issue.code}")
    }
    if (recovering) {
        PasswordRecoveryScreen(configured, email, onBack = { recoveryEmail -> email = recoveryEmail; password = ""; confirmation = ""; recovering = false }, onResetComplete = { recoveredEmail ->
            email = recoveredEmail; password = ""; confirmation = ""; register = false; completingProfile = false
            message = "Contraseña actualizada. Inicia sesión con tu nueva contraseña."; reference = ""; successNotice = true; recovering = false
        })
        return
    }
    if (policy.isNotBlank()) LegalNoticeDialog(policy) { policy = "" }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.homecheck_logo), contentDescription = "HomeCheck", modifier = Modifier.width(210.dp).height(85.dp))
        Spacer(Modifier.height(36.dp))
        Text(if (register) (if (completingProfile) "Completar registro" else "Crear cuenta") else if (role == "Propietario") "Portal del Propietario" else "Bienvenido de nuevo",
            fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Documenta el estado de tus espacios con evidencia.", modifier = Modifier.padding(vertical = 12.dp))
        if (!configured) Text("No se puede iniciar sesión: falta configurar Firebase. Contacta al equipo responsable de la aplicación.", color = MaterialTheme.colorScheme.error)
        if (completingProfile && register) Text("Tu cuenta ya existe. Completa el perfil con tu misma contraseña; no necesitas crear otra cuenta.", modifier = Modifier.padding(bottom = 12.dp))
        if (register) OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(email, { email = it; message = ""; reference = ""; completingProfile = false }, label = { Text("Correo electrónico") }, singleLine = true,
            isError = email.isNotBlank() && !emailValid,
            supportingText = { if (email.isNotBlank() && !emailValid) Text("Revisa el formato: nombre@correo.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { showPassword = !showPassword }, enabled = !busy) { Text(if (showPassword) "Ocultar" else "Mostrar", fontSize = 12.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        if (register && !completingProfile) PasswordStrengthIndicator(strength)
        if (register) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmar contraseña") },
            visualTransformation = if (showConfirmation) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { showConfirmation = !showConfirmation }, enabled = !busy) { Text(if (showConfirmation) "Ocultar" else "Mostrar", fontSize = 12.sp) } },
            isError = confirmation.isNotBlank() && password != confirmation,
            supportingText = { if (confirmation.isNotBlank() && password != confirmation) Text("Las contraseñas no coinciden. Revísalas con «Mostrar».") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        if (!register && role != "Encargado") TextButton(onClick = { role = if (role == "Propietario") "Inquilino" else "Propietario" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (role == "Propietario") "Portal de Inquilinos ›" else "¿Eres propietario? Portal de Propietarios ›")
        }
        if (register) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(accepted, { accepted = it }, enabled = !busy)
                Text("He leído las condiciones de uso y la política de privacidad.", fontSize = 13.sp)
            }
            Row {
                TextButton(onClick = { policy = "terms" }) { Text("Condiciones de uso", fontSize = 12.sp) }
                TextButton(onClick = { policy = "privacy" }) { Text("Privacidad", fontSize = 12.sp) }
            }
        }
        if (message.isNotBlank()) {
            Text(message, color = if (successNotice) Color(0xFF16845B) else MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            if (reference.isNotBlank()) {
                TextButton(onClick = { showReference = !showReference }) { Text(if (showReference) "Ocultar referencia" else "Detalle para soporte", fontSize = 12.sp) }
                if (showReference) Text("Referencia: $reference", fontSize = 12.sp)
            }
        }
        if (requirements.isNotEmpty()) Text("Para continuar: ${requirements.joinToString("; ")}.", fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = {
            busy = true; message = ""; reference = ""; successNotice = false
            scope.launch {
                try {
                    withTimeout(60_000) {
                        when {
                            register && completingProfile -> FirebaseServices.completeRegistration(name.trim(), email.trim(), password, role, accepted)
                            register -> FirebaseServices.register(name.trim(), email.trim(), password, role, accepted)
                            else -> FirebaseServices.signIn(email.trim(), password)
                        }
                    }
                    password = ""; confirmation = ""; onAuthenticated()
                } catch (error: Exception) { reportFailure(error) }
                finally { busy = false }
            }
        }, enabled = configured && !busy && requirements.isEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(54.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(if (register) (if (completingProfile) "Completar registro" else "Registrarme") else "Iniciar sesión →")
        }
        TextButton(onClick = { register = !register; message = ""; reference = "" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (register) "Ya tengo una cuenta" else "¿No tienes cuenta? Regístrate")
        }
        if (!register) TextButton(onClick = { password = ""; confirmation = ""; recovering = true; message = ""; reference = "" },
            enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("¿Olvidaste tu contraseña?") }
    }
}
