package com.casacheck.app.ui.inspection

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casacheck.app.R
import com.casacheck.app.core.formatDate
import com.casacheck.app.domain.account.AccountRole
import com.casacheck.app.domain.missingRequirements
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaPhoto
import com.casacheck.app.domain.model.CasaRoom
import com.casacheck.app.domain.model.Condition
import com.casacheck.app.ui.components.HomeCheckIcon
import com.casacheck.app.ui.theme.Blue
import com.casacheck.app.ui.theme.Green
import com.casacheck.app.ui.theme.Ink
import com.casacheck.app.ui.theme.Muted
import com.casacheck.app.ui.theme.Navy
import com.casacheck.app.ui.theme.Orange
import com.casacheck.app.ui.theme.Pale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HomeScreen(inspections: List<CasaInspection>, onCreate: () -> Unit, onOpen: (CasaInspection) -> Unit, onList: () -> Unit, onReports: () -> Unit, onInventory: () -> Unit, userName: String, role: AccountRole = AccountRole.TENANT, onWorkspace: (String) -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if (userName.isBlank()) "Hola 👋" else "Hola, ${userName.substringBefore(" ")} 👋", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(role.workspaceTitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Navy, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when(role) {
                AccountRole.OWNER -> {
                    OutlinedButton(onClick = { onWorkspace("Propiedades") },modifier = Modifier.weight(1f)) { Text("Mis propiedades") }
                    OutlinedButton(onClick = { onWorkspace("Solicitudes") },modifier = Modifier.weight(1f)) { Text("Solicitudes recibidas") }
                }
                AccountRole.TENANT -> {
                    OutlinedButton(onClick = { onWorkspace("Alquileres") },modifier = Modifier.weight(1f)) { Text("Buscar alquiler") }
                    OutlinedButton(onClick = { onWorkspace("Solicitudes") },modifier = Modifier.weight(1f)) { Text("Mis solicitudes") }
                }
                AccountRole.STAFF -> Button(onClick = { onWorkspace("Propiedades") },modifier = Modifier.fillMaxWidth()) { Text("Espacios asignados") }
            }
        }
        Text("¿Qué deseas hacer hoy?", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("＋", "Nueva inspección", onCreate, Modifier.weight(1f))
            ActionCard("☑", "Inspecciones", onList, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("▤", "Actas generadas", onReports, Modifier.weight(1f))
            ActionCard("▣", "Inventario", onInventory, Modifier.weight(1f))
        }
        Text("Inspecciones recientes", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
        if (inspections.isEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp)) {
                Text("Tu primera inspección empieza aquí", fontWeight = FontWeight.SemiBold, color = Ink)
                Text(when(role) {
                    AccountRole.OWNER -> "Registra tus inmuebles y documenta su estado."
                    AccountRole.TENANT -> "Busca un alquiler o vincula el espacio que utilizas."
                    AccountRole.STAFF -> "Acepta el encargo del propietario antes de inspeccionar un espacio."
                }, color = Muted, modifier = Modifier.padding(top = 5.dp))
            }
        } else inspections.sortedByDescending { it.createdAt }.take(5).forEach { InspectionCard(it) { onOpen(it) } }
        Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Navy)) {
            Column(Modifier.padding(16.dp)) {
                Text("Consejo", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Repite el encuadre y la iluminación en la inspección Después.", color = Color.White.copy(alpha = .86f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable internal fun ActionCard(icon: String, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) { HomeCheckIcon(when (title) { "Nueva inspección" -> "add"; "Inventario" -> "inventory"; "Inspecciones" -> "checklist"; else -> "document" }, title, Modifier.size(24.dp), Blue); Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 6.dp)); Text(when (title) { "Inspecciones" -> "Ver todas"; "Actas generadas" -> "Ver reportes"; "Inventario" -> "Ver mobiliario"; else -> "Crear nueva" }, fontSize = 12.sp, color = Muted) }
    }
}
@Composable internal fun InspectionCard(inspection: CasaInspection, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(inspection.propertyName, fontWeight = FontWeight.Bold, color = Ink)
                Text("${inspection.phase} · ${inspection.type} · ${formatDate(inspection.createdAt)}", fontSize = 12.sp, color = Muted)
            }
            Text(if (inspection.completed) "Completa" else "Borrador", color = if (inspection.completed) Green else Orange, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun CreateScreen(initialType: String, onType: (String) -> Unit, onCreate: (String, String, String) -> Unit) {
    var type by remember { mutableStateOf(initialType) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    val eventTypes = listOf("Salón", "Finca", "Quinta", "Cancha")
    val residential = type !in eventTypes
    val choices = if (residential) listOf("Casa", "Apartamento", "Cuarto universitario") else eventTypes
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("¿Qué espacio vas a inspeccionar?", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Elige una plantilla para preparar el checklist.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Residencial", "Eventos").forEach { category ->
                FilterChip(selected = (category == "Residencial") == residential, onClick = { type = if (category == "Residencial") "Casa" else "Salón"; onType(type) }, label = { Text(category) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { choice -> FilterChip(selected = choice == type, onClick = { type = choice; onType(choice) }, label = { Text(choice) }) }
        }
        listOf(true, false).forEach { isResidential ->
            val selected = residential == isResidential
            OutlinedCard(onClick = { type = if (isResidential) "Casa" else "Salón"; onType(type) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Navy else Color(0xFFDDDFE3))) {
                Image(painterResource(if (isResidential) R.drawable.residential else R.drawable.events),
                    if (isResidential) "Vivienda residencial" else "Salón de eventos", Modifier.fillMaxWidth().height(175.dp), contentScale = ContentScale.Crop)
                Column(Modifier.padding(14.dp)) {
                    Text(if (isResidential) "Alquiler Residencial" else "Salón de Eventos", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(if (isResidential) "Habitaciones, cocina, baños y zonas comunes de viviendas particulares." else "Inventario de mobiliario, baños y áreas comunes de salones y fincas.", color = Muted, fontSize = 13.sp)
                }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Nombre del inmueble") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(address, { address = it }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(15.dp))
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = { if (name.isNotBlank() && address.isNotBlank()) onCreate(name.trim(), address.trim(), type) }, enabled = name.isNotBlank() && address.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Crear inspección Antes  →") }
    }
}

@Composable
internal fun InspectionList(inspections: List<CasaInspection>, reports: Boolean, onOpen: (CasaInspection) -> Unit, onCreate: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var phase by rememberSaveable { mutableStateOf("Todas") }
    val filtered = inspections.filter { it.propertyName.contains(query, ignoreCase = true) && (phase == "Todas" || it.phase == phase) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text(if (reports) "Actas firmadas en este dispositivo" else "Continúa un borrador o inicia una inspección.", color = Muted, modifier = Modifier.padding(bottom = 12.dp))
        OutlinedTextField(query, { query = it }, label = { Text(if (reports) "Buscar actas" else "Buscar inspecciones") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todas", "ANTES", "DESPUÉS").forEach { choice -> FilterChip(phase == choice, { phase = choice }, label = { Text(choice) }) }
        }
        Text("${filtered.size} resultados", color = Muted, fontSize = 12.sp)
        if (inspections.isEmpty()) Text(if (reports) "Aún no hay actas generadas." else "No hay inspecciones pendientes.", color = Muted)
        LazyColumn(Modifier.weight(1f)) { items(filtered.sortedByDescending { it.createdAt }, key = { it.id }) { InspectionCard(it) { onOpen(it) } } }
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("＋ Nueva inspección") }
    }
}

@Composable
internal fun RoomsScreen(inspection: CasaInspection, onOpen: (CasaRoom) -> Unit, onSummary: () -> Unit, onAdd: (String) -> Unit) {
    var roomName by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Agregar ambiente") },
        text = { OutlinedTextField(roomName, { roomName = it.take(80) }, label = { Text("Nombre del ambiente") }) },
        confirmButton = { TextButton(onClick = { onAdd(roomName.trim()); roomName = ""; adding = false }, enabled = roomName.isNotBlank()) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancelar") } })
    val all = inspection.rooms.flatMap { it.items }
    val reviewed = all.count { it.condition != Condition.PENDING }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(inspection.propertyName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("${inspection.phase} · ${inspection.type} · ${inspection.address}", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 13.dp))
        LinearProgressIndicator(progress = { if (all.isEmpty()) 0f else reviewed.toFloat() / all.size }, modifier = Modifier.fillMaxWidth())
        Text("$reviewed de ${all.size} elementos revisados", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(vertical = 7.dp))
        inspection.rooms.forEach { r ->
            val done = r.items.all { it.condition != Condition.PENDING && (!it.critical || it.photoPath.isNotBlank()) }
            Card(Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable { onOpen(r) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = FontWeight.Bold, color = Ink)
                        Text("${r.items.count { it.condition != Condition.PENDING }} / ${r.items.size} revisados · ${r.items.count { it.critical }} fotos críticas", fontSize = 12.sp, color = Muted)
                    }
                    Text(if (done) "✓" else "›", fontSize = 22.sp, color = if (done) Green else Blue)
                }
            }
        }
        OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Agregar ambiente") }
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onSummary, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Revisar resumen  →") }
    }
}

@Composable
internal fun ChecklistScreen(room: CasaRoom, phase: String, onOpen: (CasaItem) -> Unit, onNext: () -> Unit) {
    val done = room.items.count { it.condition != Condition.PENDING }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text(room.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("$phase · $done de ${room.items.size} revisados", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        LinearProgressIndicator(progress = { if (room.items.isEmpty()) 0f else done.toFloat() / room.items.size }, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
            items(room.items, key = { it.id }) { x ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpen(x) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(x.name, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(if (x.condition == Condition.PENDING) "Pendiente de revisar" else "${x.condition.label} · ${x.quantity} unidad(es)", fontSize = 12.sp, color = Muted)
                            if (x.critical) Text(if (x.photoPath.isBlank()) "Foto obligatoria" else "✓ Foto guardada", fontSize = 12.sp, color = if (x.photoPath.isBlank()) Orange else Green)
                        }
                        Text("›", fontSize = 24.sp, color = Blue)
                    }
                }
            }
        }
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Siguiente ambiente  →") }
    }
}

@Composable
internal fun ItemScreen(item: CasaItem, onPhoto: (File, String) -> Unit, onSave: (CasaItem) -> Unit, busy: Boolean) {
    var condition by rememberSaveable(item.id) { mutableStateOf(item.condition) }
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity.toString()) }
    var notes by rememberSaveable(item.id) { mutableStateOf(item.notes) }
    val count = quantity.toIntOrNull() ?: -1
    val canSave = !busy && condition != Condition.PENDING && count in 0..9999 &&
        (condition == Condition.MISSING || count > 0) &&
        (condition != Condition.MISSING || count == 0) &&
        (condition == Condition.GOOD || notes.isNotBlank()) &&
        (!item.critical || item.photoPath.isNotBlank())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(if (item.critical) "Elemento crítico · necesita fotografía" else "Registra el estado observado", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        if (item.extraPhotos.size < 7) CameraPanel(busy, onPhoto)
        else Text("Alcanzaste el límite de ocho fotografías por elemento.", color = Muted)
        EvidenceStrip(item)
        Spacer(Modifier.height(16.dp))
        Text("Estado", fontWeight = FontWeight.SemiBold, color = Ink)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Condition.entries.filter { it != Condition.PENDING }.forEach { option -> FilterChip(condition == option, { condition = option }, label = { Text(option.label, fontSize = 12.sp) }) }
        }
        OutlinedTextField(quantity, { if (it.length <= 4 && it.all(Char::isDigit)) quantity = it }, label = { Text("Cantidad (0–9999)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.height(13.dp))
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = { onSave(item.copy(condition = condition, quantity = count, notes = notes.trim())) }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar elemento  ✓") }
        Text("Los estados distintos de Bueno requieren observaciones. Los faltantes llevan cantidad cero.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun SummaryScreen(i: CasaInspection, baseline: CasaInspection?, onSign: () -> Unit, onCompare: () -> Unit) {
    val missing = missingRequirements(i)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(i.propertyName, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("${i.type} · ${i.phase}", color = Muted)
        Spacer(Modifier.height(14.dp))
        i.rooms.forEach { r ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(14.dp)) {
                    Text(r.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("${r.items.count { it.condition != Condition.PENDING }} / ${r.items.size}", color = Muted)
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = if (missing.isEmpty()) Color(0xFFEAF7F1) else Color(0xFFFFF4E6))) {
            Column(Modifier.padding(15.dp)) {
                Text(if (missing.isEmpty()) "✓ Lista para firmar" else "${missing.size} elementos pendientes", fontWeight = FontWeight.Bold, color = if (missing.isEmpty()) Green else Orange)
                if (missing.isNotEmpty()) Text("Primero: ${missing.first()}", color = Muted, fontSize = 13.sp)
            }
        }
        if (baseline != null) OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onCompare, modifier = Modifier.fillMaxWidth()) { Text("Comparar Antes y Después") }
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onSign, enabled = missing.isEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Firmar y generar acta") }
    }
}

@Composable
internal fun ComparisonScreen(after: CasaInspection, before: CasaInspection?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Cambios entre inspecciones", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (before == null) Text("No se encontró el acta Antes vinculada.", color = Muted)
        else after.rooms.forEach { r ->
            val oldRoom = before.rooms.firstOrNull { it.id == r.id }
            Text(r.name, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            r.items.forEach { x ->
                val old = oldRoom?.items?.firstOrNull { it.id == x.id }
                val changed = old != null && (old.condition != x.condition || old.quantity != x.quantity)
                Card(Modifier.fillMaxWidth().padding(bottom = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(x.name, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Antes: ${old?.condition?.label ?: "Sin registro"} · ${old?.quantity ?: 0} → Después: ${x.condition.label} · ${x.quantity}", fontSize = 12.sp, color = Muted)
                        if (changed && x.condition != Condition.PENDING) Text("Cambio de estado o cantidad", color = Orange, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EvidencePreview(old?.photoPath.orEmpty(), "Antes", Modifier.weight(1f))
                            EvidencePreview(x.photoPath, "Después", Modifier.weight(1f))
                        }
                        if (!old?.photoPath.isNullOrBlank() || x.photoPath.isNotBlank()) Text("Evidencia fotográfica disponible", color = Blue, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EvidencePreview(path: String, label: String, modifier: Modifier = Modifier) {
    val image = remember(path) {
        if (path.isBlank()) null else BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
    }
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Muted)
        if (image == null) Text("Sin fotografía", fontSize = 12.sp, color = Muted)
        else Image(image, contentDescription = "Evidencia $label", modifier = Modifier.fillMaxWidth().height(150.dp))
    }
}

@Composable
internal fun InventoryScreen(inspections: List<CasaInspection>) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar propiedad o elemento") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (inspections.isEmpty()) Text("Aún no hay inventario. Crea tu primera inspección.")
        LazyColumn(Modifier.weight(1f)) {
            inspections.sortedByDescending { it.createdAt }.forEach { inspection ->
                val rows = inspection.rooms.flatMap { room -> room.items.map { room.name to it } }
                    .filter { (room, item) -> "${inspection.propertyName} $room ${item.name}".contains(query, ignoreCase = true) }
                if (rows.isNotEmpty()) {
                    item(key = inspection.id) { Text("${inspection.propertyName} · ${inspection.phase}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }
                    items(rows, key = { "${inspection.id}_${it.second.id}" }) { (room, item) ->
                        ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("$room · ${item.condition.label}") }, trailingContent = { Text("${item.quantity}") })
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActaPreviewScreen(inspection: CasaInspection, baseline: CasaInspection?, onSign: () -> Unit) {
    val changes = inspection.rooms.sumOf { room -> room.items.count { item ->
        val old = baseline?.rooms?.firstOrNull { it.id == room.id }?.items?.firstOrNull { it.id == item.id }
        old != null && (old.condition != item.condition || old.quantity != item.quantity)
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Vista previa del acta", color = Muted, fontSize = 12.sp)
        Text(if (baseline == null) "ACTA DE INSPECCIÓN" else "ACTA DE INSPECCIÓN COMPARATIVA", fontSize = 25.sp, color = Navy, fontWeight = FontWeight.Bold)
        Text(inspection.propertyName, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text("${inspection.type} · ${inspection.address}", color = Muted)
        HorizontalDivider()
        Text("FECHAS", fontWeight = FontWeight.Bold, color = Navy)
        if (baseline != null) Text("Antes: ${formatDate(baseline.createdAt)}")
        Text("${inspection.phase}: ${formatDate(inspection.createdAt)}")
        HorizontalDivider()
        Text("RESUMEN", fontWeight = FontWeight.Bold, color = Navy)
        Text("Ambientes inspeccionados: ${inspection.rooms.size}")
        if (baseline != null) Text("Cambios de estado o cantidad: $changes")
        Text("Evidencia fotográfica: ${inspection.rooms.sumOf { room -> room.items.sumOf { it.extraPhotos.size + if (it.photoPath.isBlank()) 0 else 1 } }} imágenes")
        Text("El documento final incluye inventario, observaciones completas, evidencia de ambas fases y las firmas trazadas por las partes.", color = Muted)
        inspection.rooms.forEach { room -> Text("${room.name}: ${room.items.size} elementos revisados") }
        Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onSign, enabled = missingRequirements(inspection).isEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Continuar a firmas") }
    }
}

@Composable
private fun EvidenceStrip(item: CasaItem) {
    val photos = (if (item.photoPath.isBlank()) emptyList() else listOf(CasaPhoto(item.photoPath, item.capturedAt, item.photoOrigin))) + item.extraPhotos
    var expanded by remember { mutableStateOf(false) }
    if (expanded) androidx.compose.ui.window.Dialog(onDismissRequest = { expanded = false }) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Evidencia fotográfica", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                photos.forEachIndexed { index, photo ->
                    EvidencePreview(photo.path, "${index + 1}. ${if (photo.origin == "GALLERY") "Importada" else "Capturada"} · ${formatDate(photo.capturedAt)}", Modifier.fillMaxWidth())
                }
                TextButton(onClick = { expanded = false }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Evidencia capturada · ${photos.size}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 13.sp)
        TextButton(onClick = { expanded = true }, enabled = photos.isNotEmpty()) { Text("Ver todas") }
    }
    if (photos.isEmpty()) Text("Aún no has agregado fotografías.", color = Muted, fontSize = 12.sp)
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEachIndexed { index, photo ->
            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.path) {
                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }
            }
            Column(Modifier.width(88.dp).clickable { expanded = true }) {
                val thumbnail = image
                if (thumbnail != null) Image(thumbnail, "Evidencia ${index + 1}", Modifier.size(88.dp), contentScale = ContentScale.Crop)
                else Box(Modifier.size(88.dp).background(Pale), contentAlignment = Alignment.Center) { Text("Sin vista previa", fontSize = 11.sp) }
                Text(if (photo.origin == "GALLERY") "Importada" else "Cámara", fontSize = 11.sp, color = Muted)
            }
        }
    }
}
