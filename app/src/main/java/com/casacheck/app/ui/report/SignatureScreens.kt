package com.casacheck.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casacheck.app.core.encodeSignature
import com.casacheck.app.core.parseSignature
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.ui.theme.Green
import com.casacheck.app.ui.theme.Ink
import com.casacheck.app.ui.theme.Muted
import com.casacheck.app.ui.theme.Navy

@Composable
internal fun SignatureScreen(i: CasaInspection, busy: Boolean, onFinish: (String, String, String, String) -> Unit) {
    var owner by rememberSaveable { mutableStateOf(i.ownerName) }
    var renter by rememberSaveable { mutableStateOf(i.renterName) }
    var ownerSign by rememberSaveable { mutableStateOf(i.ownerSignature) }
    var renterSign by rememberSaveable { mutableStateOf(i.renterSignature) }
    var accepted by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Requiere firma de ambas partes", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Revisen el inventario y las evidencias antes de aceptar el acta.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        OutlinedTextField(owner, { owner = it }, label = { Text("Propietario / encargado") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        SignaturePad(ownerSign, { ownerSign = it }, "Firma del propietario")
        OutlinedTextField(renter, { renter = it }, label = { Text("Inquilino / responsable") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        SignaturePad(renterSign, { renterSign = it }, "Firma del inquilino")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(accepted, { accepted = it })
            Text("Ambas partes aceptan el estado registrado.", color = Ink)
        }
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = { onFinish(owner.trim(), renter.trim(), ownerSign, renterSign) }, enabled = !busy && owner.isNotBlank() && renter.isNotBlank() && ownerSign.isNotBlank() && renterSign.isNotBlank() && accepted, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (busy) "Generando acta…" else "Generar acta PDF") }
    }
}

@Composable
internal fun SignaturePad(value: String, onChange: (String) -> Unit, label: String) {
    var strokes by remember(value) { mutableStateOf(parseSignature(value)) }
    var live by remember { mutableStateOf(emptyList<Offset>()) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { 132.dp.toPx() }
        Canvas(Modifier.testTag(label).semantics { contentDescription = label }.fillMaxWidth().height(132.dp).padding(top = 5.dp).background(Color.White, RoundedCornerShape(10.dp))
            .pointerInput(value, widthPx, heightPx) {
                detectDragGestures(
                    onDragStart = { p -> live = listOf(Offset((p.x / widthPx).coerceIn(0f, 1f), (p.y / heightPx).coerceIn(0f, 1f))) },
                    onDrag = { change, _ -> change.consume(); live = live + Offset((change.position.x / widthPx).coerceIn(0f, 1f), (change.position.y / heightPx).coerceIn(0f, 1f)) },
                    onDragCancel = { live = emptyList() },
                    onDragEnd = { if (live.size > 1) { strokes = strokes + listOf(live); live = emptyList(); onChange(encodeSignature(strokes)) } }
                )
            }
        ) {
            drawLine(Color.LightGray, Offset(0f, size.height - 10f), Offset(size.width, size.height - 10f), 1.dp.toPx())
            (strokes + listOf(live).filter { it.isNotEmpty() }).forEach { points ->
                if (points.size > 1) {
                    val path = Path().apply { moveTo(points.first().x * size.width, points.first().y * size.height); points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) } }
                    drawPath(path, Navy, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = Muted)
        TextButton(onClick = { strokes = emptyList(); live = emptyList(); onChange("") }) { Text("Borrar") }
    }
}

@Composable
internal fun SuccessScreen(i: CasaInspection, onOpen: () -> Unit, onShare: () -> Unit, onAfter: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text("✓", fontSize = 72.sp, color = Green, fontWeight = FontWeight.Bold)
        Text("Acta generada exitosamente", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(i.propertyName, color = Muted, modifier = Modifier.padding(top = 7.dp, bottom = 18.dp))
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onOpen, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("▤  Ver PDF") }
        OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Compartir acta") }
        if (i.phase == "ANTES") OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onAfter, modifier = Modifier.fillMaxWidth()) { Text("Iniciar inspección Después") }
        TextButton(onClick = onHome) { Text("Volver al inicio") }
    }
}
