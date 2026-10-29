package com.casacheck.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import com.casacheck.app.ui.account.AccountScreen
import org.junit.Rule
import org.junit.Test

class AccountAccessTest {
    @get:Rule val compose = createComposeRule()
    @Test fun accessRequiresFirebaseAndOffersNoGuestEntry() {
        compose.setContent { MaterialTheme { AccountScreen(configured = false, onAuthenticated = {}) } }
        compose.onNodeWithText("Continuar en modo local").assertDoesNotExist()
        compose.onNodeWithText("Iniciar sesión →").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("No se puede iniciar sesión: falta configurar Firebase. Contacta al equipo responsable de la aplicación.").assertExists()
        compose.onNodeWithText("¿No tienes cuenta? Regístrate").performScrollTo().performClick()
        compose.onNodeWithText("Continuar en modo local").assertDoesNotExist()
        compose.onNodeWithText("Registrarme").performScrollTo().assertIsNotEnabled()
    }
    @Test fun registrationShowsStrengthRequirementsAndPasswordMismatch() {
        compose.setContent { MaterialTheme { AccountScreen(configured = true, onAuthenticated = {}) } }
        compose.onNodeWithText("¿No tienes cuenta? Regístrate").performScrollTo().performClick()
        compose.onNodeWithText("Contraseña", substring = false).performScrollTo().performTextInput("Ab1")
        compose.onNodeWithText("Fortaleza: Débil").assertExists()
        compose.onNodeWithText("Mínimo requerido: 8 caracteres").assertExists()
        compose.onNodeWithText("Contraseña", substring = false).performTextClearance()
        compose.onNodeWithText("Contraseña", substring = false).performTextInput("MiClaveLarga!42")
        compose.onNodeWithText("Fortaleza: Fuerte").assertExists()
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().performTextInput("OtraClave!42")
        compose.onNodeWithText("Las contraseñas no coinciden. Revísalas con «Mostrar».").assertExists()
        compose.onNodeWithText("Registrarme").performScrollTo().assertIsNotEnabled()
    }

    @Test fun forgotPasswordOpensADedicatedScreenWithoutRequiringEmailOrPassword() {
        compose.setContent { MaterialTheme { AccountScreen(configured = false, onAuthenticated = {}) } }
        compose.onNodeWithText("Contraseña", substring = false).performScrollTo().performTextInput("OldWrong!23")
        compose.onNodeWithText("¿Olvidaste tu contraseña?").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Recuperar contraseña").assertExists()
        compose.onNodeWithText("Correo de la cuenta").performTextInput("corrected-qa@example.com")
        compose.onNodeWithText("Contraseña", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Enviar enlace de recuperación").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Volver a iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Bienvenido de nuevo").assertExists()
        compose.onNodeWithText("Correo electrónico").assertTextContains("corrected-qa@example.com")
        compose.onNodeWithText("Contraseña", substring = false).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

}
