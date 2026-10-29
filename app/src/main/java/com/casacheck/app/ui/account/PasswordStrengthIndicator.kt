package com.casacheck.app.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casacheck.app.domain.account.PasswordAssessment

@Composable
internal fun PasswordStrengthIndicator(value: PasswordAssessment) {
    val color = when (value.level) { 3 -> Color(0xFF16845B); 2 -> Color(0xFF9A6300); else -> MaterialTheme.colorScheme.error }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("password-strength"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Fortaleza: ${value.label}", color = color)
        LinearProgressIndicator(progress = { value.level / 3f }, color = color, modifier = Modifier.fillMaxWidth())
        Text(if (value.longEnough) "✓ Mínimo de 8 caracteres cumplido" else "Mínimo requerido: 8 caracteres", fontSize = 12.sp)
        Text(listOf(value.lowercase to "minúscula", value.uppercase to "mayúscula", value.number to "número", value.symbol to "símbolo")
            .joinToString(" · ") { (met, label) -> "${if (met) "✓" else "○"} $label" }, fontSize = 12.sp)
        Text("Indicador orientativo. Recomendamos 12 o más caracteres y variedad; no reutilices contraseñas.", fontSize = 12.sp)
    }
}
