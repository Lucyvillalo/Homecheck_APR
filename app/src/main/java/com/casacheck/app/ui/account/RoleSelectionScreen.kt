package com.casacheck.app.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casacheck.app.R
import com.casacheck.app.ui.components.HomeCheckIcon
import com.casacheck.app.ui.theme.Blue
import com.casacheck.app.ui.theme.Ink
import com.casacheck.app.ui.theme.Muted

@Composable
internal fun RoleSelectionScreen(onContinue: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("Inquilino") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxHeight().padding(24.dp)) {
          Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Image(painterResource(R.drawable.homecheck_logo), "HomeCheck", Modifier.width(150.dp).height(70.dp))
            Spacer(Modifier.height(32.dp))
            Text("¿Cómo usarás la app?", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("El perfil elegido se utiliza al registrar una cuenta. Si ya tienes cuenta, al iniciar sesión se recupera su rol guardado.", color = Muted, modifier = Modifier.padding(top = 12.dp, bottom = 28.dp))
            listOf("Inquilino" to "Inquilino", "Propietario" to "Propietario", "Encargado" to "Encargado").forEach { (role, title) ->
                OutlinedCard(onClick = { selected = role }, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(if (selected == role) 2.dp else 1.dp, if (selected == role) Blue else androidx.compose.ui.graphics.Color(0xFFDDDFE3))) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                        HomeCheckIcon(if (role == "Inquilino") "home" else "checklist", title, Modifier.size(30.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(title, fontWeight = FontWeight.Bold, color = Ink)
                            Text(if (role == "Inquilino") "Consulta alquileres y registra inspecciones de los espacios que utilizas." else if (role == "Propietario") "Publica alquileres, vincula inquilinos y comparte los datos de las inspecciones." else "Acepta asignaciones de un propietario y revisa inspecciones, inventario e incidencias. No administras alquileres ni publicaciones.", color = Muted, modifier = Modifier.padding(top = 10.dp), fontSize = 14.sp)
                        }
                        RadioButton(selected == role, onClick = { selected = role }, modifier = Modifier.size(24.dp))
                    }
                }
            }
          }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onContinue(selected) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(8.dp)) { Text("Continuar") }
        }
    }
}
