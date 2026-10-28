package com.casacheck.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF123B66)
private val Blue = Color(0xFF1B67B2)
private val Pale = Color(0xFFF4F7FB)
private val Ink = Color(0xFF172536)
private val Muted = Color(0xFF6B7C8F)
private val Green = Color(0xFF16845B)
private val Orange = Color(0xFFB76B16)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CasaCheckApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CasaCheckApp() {
    val context = LocalContext.current
    var inspections by remember { mutableStateOf(CasaStore.load(context)) }
    var screen by remember { mutableStateOf("inicio") }
    var selectedId by remember { mutableStateOf("") }
    var selectedRoom by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf("") }
    var createType by remember { mutableStateOf("Casa") }
    LaunchedEffect(inspections) { CasaStore.save(context, inspections) }
    val current = inspections.firstOrNull { it.id == selectedId }
    val room = current?.rooms?.firstOrNull { it.id == selectedRoom }
    val item = room?.items?.firstOrNull { it.id == selectedItem }

    fun updateInspection(updated: CasaInspection) {
        inspections = inspections.map { if (it.id == updated.id) updated else it }
    }
    fun changeItem(updated: CasaItem) {
        val i = current ?: return
        val r = room ?: return
        updateInspection(i.copy(rooms = i.rooms.map { a -> if (a.id == r.id) a.copy(items = a.items.map { x -> if (x.id == updated.id) updated else x }) else a }))
    }
    fun back() {
        screen = when (screen) {
            "crear", "actas", "inspecciones" -> "inicio"
            "ambientes" -> "inspecciones"
            "checklist" -> "ambientes"
            "elemento" -> "checklist"
            "resumen", "comparar" -> "ambientes"
            "firmas" -> "resumen"
            "exito" -> "actas"
            else -> "inicio"
        }
    }
    BackHandler(enabled = screen != "inicio") { back() }

    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Blue, background = Pale, surface = Color.White)) {
        Scaffold(
            containerColor = Pale,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (screen == "inicio") "CasaCheck" else titleFor(screen), fontWeight = FontWeight.Bold, color = Navy)
                            if (current != null && screen !in listOf("inicio", "crear", "actas", "inspecciones")) Text("${current.propertyName} · ${current.phase}", fontSize = 12.sp, color = Muted)
                        }
                    },
                    navigationIcon = { if (screen != "inicio") TextButton(onClick = { back() }) { Text("‹", fontSize = 26.sp, color = Navy) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                if (screen in listOf("inicio", "inspecciones", "actas")) NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(selected = screen == "inicio", onClick = { screen = "inicio" }, icon = { Text("⌂") }, label = { Text("Inicio") })
                    NavigationBarItem(selected = screen == "inspecciones", onClick = { screen = "inspecciones" }, icon = { Text("☑") }, label = { Text("Inspecciones") })
                    NavigationBarItem(selected = screen == "actas", onClick = { screen = "actas" }, icon = { Text("▤") }, label = { Text("Actas") })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    "inicio" -> HomeScreen(inspections, { screen = "crear" }, { i -> selectedId = i.id; screen = if (i.completed) "exito" else "ambientes" }, { screen = "inspecciones" }, { screen = "actas" })
                    "crear" -> CreateScreen(createType, { createType = it }) { name, address, type ->
                        val created = CasaInspection(propertyName = name, address = address, type = type, rooms = roomsFor(type))
                        inspections = inspections + created; selectedId = created.id; screen = "ambientes"
                    }
                    "inspecciones" -> InspectionList(inspections.filter { !it.completed }, false, { i -> selectedId = i.id; screen = "ambientes" }, { screen = "crear" })
                    "actas" -> InspectionList(inspections.filter { it.completed }, true, { i -> selectedId = i.id; screen = "exito" }, { screen = "crear" })
                    "ambientes" -> current?.let { inspection ->
                        RoomsScreen(inspection, { selectedRoom = it.id; screen = "checklist" }, { screen = "resumen" })
                    }
                    "checklist" -> if (current != null && room != null) ChecklistScreen(room, current.phase, { selectedItem = it.id; screen = "elemento" }) {
                        val idx = current.rooms.indexOfFirst { it.id == room.id }
                        if (idx + 1 < current.rooms.size) selectedRoom = current.rooms[idx + 1].id else screen = "ambientes"
                    }
                    "elemento" -> if (current != null && room != null && item != null) ItemScreen(item, { bitmap ->
                        val path = saveWatermarkedPhoto(context, bitmap, current.id, item.id)
                        changeItem(item.copy(photoPath = path, capturedAt = System.currentTimeMillis()))
                    }, { updated -> changeItem(updated); screen = "checklist" })
                    "resumen" -> current?.let { SummaryScreen(it, inspections.firstOrNull { b -> b.id == it.baselineId }, { screen = "firmas" }, { screen = "comparar" }) }
                    "comparar" -> current?.let { ComparisonScreen(it, inspections.firstOrNull { b -> b.id == it.baselineId }) }
                    "firmas" -> current?.let { inspection -> SignatureScreen(inspection) { owner, renter, ownerSign, renterSign ->
                        val signed = inspection.copy(ownerName = owner, renterName = renter, ownerSignature = ownerSign, renterSignature = renterSign)
                        val pdf = createActaPdf(context, signed, inspections.firstOrNull { it.id == signed.baselineId })
                        updateInspection(signed.copy(completed = true, reportPath = pdf.absolutePath)); screen = "exito"
                    } }
                    "exito" -> current?.let { inspection -> SuccessScreen(inspection, { openPdf(context, inspection.reportPath, false) }, { openPdf(context, inspection.reportPath, true) }, {
                        val next = inspections.firstOrNull { it.baselineId == inspection.id && !it.completed } ?: newAfter(inspection).also { inspections = inspections + it }
                        selectedId = next.id; screen = "ambientes"
                    }, { screen = "inicio" }) }
                }
            }
        }
    }
}

private fun titleFor(screen: String) = when (screen) {
    "crear" -> "Nueva inspección"; "inspecciones" -> "Inspecciones"; "actas" -> "Mis actas"
    "ambientes" -> "Ambientes"; "checklist" -> "Checklist"; "elemento" -> "Estado del elemento"
    "resumen" -> "Resumen"; "comparar" -> "Comparación"; "firmas" -> "Firmas"; else -> "Acta generada"
}

@Composable
private fun HomeScreen(inspections: List<CasaInspection>, onCreate: () -> Unit, onOpen: (CasaInspection) -> Unit, onList: () -> Unit, onReports: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Documenta cada espacio con claridad.", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Inspecciones Antes y Después para alquileres y eventos.", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Propiedades", inspections.map { it.propertyId }.distinct().size.toString(), Modifier.weight(1f))
            StatCard("Actas listas", inspections.count { it.completed }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(15.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("＋  Nueva inspección", fontSize = 16.sp) }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard("☑", "Inspecciones", onList, Modifier.weight(1f))
            ActionCard("▤", "Actas generadas", onReports, Modifier.weight(1f))
        }
        Text("Recientes", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
        if (inspections.isEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp)) {
                Text("Tu primera inspección empieza aquí", fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Crea una propiedad y revisa sus ambientes con fotos.", color = Muted, modifier = Modifier.padding(top = 5.dp))
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

@Composable private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) { Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy); Text(label, color = Muted) }
    }
}
@Composable private fun ActionCard(icon: String, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) { Text(icon, fontSize = 22.sp, color = Blue); Text(title, fontWeight = FontWeight.SemiBold, color = Navy, modifier = Modifier.padding(top = 6.dp)) }
    }
}
@Composable private fun InspectionCard(inspection: CasaInspection, onClick: () -> Unit) {
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
private fun CreateScreen(initialType: String, onType: (String) -> Unit, onCreate: (String, String, String) -> Unit) {
    var type by remember { mutableStateOf(initialType) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
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
        Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text(if (residential) "⌂  Alquiler residencial" else "▧  Espacio para eventos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(if (residential) "Habitaciones, cocina, baños y zonas comunes." else "Zonas, mobiliario, baños y áreas de servicio.", color = Muted, modifier = Modifier.padding(top = 5.dp))
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Nombre del inmueble") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(address, { address = it }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(15.dp))
        Button(onClick = { if (name.isNotBlank() && address.isNotBlank()) onCreate(name.trim(), address.trim(), type) }, enabled = name.isNotBlank() && address.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Crear inspección Antes  →") }
    }
}

@Composable
private fun InspectionList(inspections: List<CasaInspection>, reports: Boolean, onOpen: (CasaInspection) -> Unit, onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text(if (reports) "Actas firmadas en este dispositivo" else "Continúa un borrador o inicia una inspección.", color = Muted, modifier = Modifier.padding(bottom = 12.dp))
        if (inspections.isEmpty()) Text(if (reports) "Aún no hay actas generadas." else "No hay inspecciones pendientes.", color = Muted)
        LazyColumn(Modifier.weight(1f)) { items(inspections.sortedByDescending { it.createdAt }, key = { it.id }) { InspectionCard(it) { onOpen(it) } } }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("＋ Nueva inspección") }
    }
}

@Composable
private fun RoomsScreen(inspection: CasaInspection, onOpen: (CasaRoom) -> Unit, onSummary: () -> Unit) {
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
        Button(onClick = onSummary, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Revisar resumen  →") }
    }
}

@Composable
private fun ChecklistScreen(room: CasaRoom, phase: String, onOpen: (CasaItem) -> Unit, onNext: () -> Unit) {
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
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Siguiente ambiente  →") }
    }
}

@Composable
private fun ItemScreen(item: CasaItem, onPhoto: (Bitmap) -> Unit, onSave: (CasaItem) -> Unit) {
    var condition by remember(item.id) { mutableStateOf(item.condition) }
    var quantity by remember(item.id) { mutableStateOf(item.quantity.toString()) }
    var notes by remember(item.id) { mutableStateOf(item.notes) }
    val count = quantity.toIntOrNull() ?: -1
    val canSave = condition != Condition.PENDING && count in 0..9999 &&
        (condition != Condition.MISSING || count == 0) &&
        (condition == Condition.GOOD || notes.isNotBlank()) &&
        (!item.critical || item.photoPath.isNotBlank())
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { image -> image?.let(onPhoto) }
    val preview = remember(item.photoPath) { if (item.photoPath.isNotBlank()) BitmapFactory.decodeFile(item.photoPath)?.asImageBitmap() else null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(if (item.critical) "Elemento crítico · necesita fotografía" else "Registra el estado observado", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Text("Estado", fontWeight = FontWeight.SemiBold, color = Ink)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Condition.entries.filter { it != Condition.PENDING }.forEach { option -> FilterChip(condition == option, { condition = option }, label = { Text(option.label, fontSize = 12.sp) }) }
        }
        OutlinedTextField(quantity, { if (it.length <= 4 && it.all(Char::isDigit)) quantity = it }, label = { Text("Cantidad (0–9999)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { camera.launch(null) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (item.photoPath.isBlank()) "◎  Tomar fotografía" else "◎  Repetir fotografía") }
        if (preview != null) {
            Image(preview, contentDescription = "Foto de ${item.name}", modifier = Modifier.fillMaxWidth().height(185.dp).padding(top = 8.dp))
            Text("Foto con fecha y hora: ${formatDate(item.capturedAt)}", fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(13.dp))
        Button(onClick = { onSave(item.copy(condition = condition, quantity = count, notes = notes.trim())) }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar elemento  ✓") }
        Text("Los estados distintos de Bueno requieren observaciones. Los faltantes llevan cantidad cero.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SummaryScreen(i: CasaInspection, baseline: CasaInspection?, onSign: () -> Unit, onCompare: () -> Unit) {
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
        if (baseline != null) OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth()) { Text("Comparar Antes y Después") }
        Button(onClick = onSign, enabled = missing.isEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Firmar y generar acta") }
    }
}

@Composable
private fun ComparisonScreen(after: CasaInspection, before: CasaInspection?) {
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
                        if (changed) Text("Cambio de estado o cantidad", color = Orange, fontSize = 12.sp)
                        if (!old?.photoPath.isNullOrBlank() || x.photoPath.isNotBlank()) Text("Evidencia fotográfica disponible", color = Blue, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureScreen(i: CasaInspection, onFinish: (String, String, String, String) -> Unit) {
    var owner by remember { mutableStateOf(i.ownerName) }
    var renter by remember { mutableStateOf(i.renterName) }
    var ownerSign by remember { mutableStateOf(i.ownerSignature) }
    var renterSign by remember { mutableStateOf(i.renterSignature) }
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
        Button(onClick = { onFinish(owner.trim(), renter.trim(), ownerSign, renterSign) }, enabled = owner.isNotBlank() && renter.isNotBlank() && ownerSign.isNotBlank() && renterSign.isNotBlank() && accepted, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Generar acta PDF") }
    }
}

@Composable
private fun SignaturePad(value: String, onChange: (String) -> Unit, label: String) {
    var strokes by remember(value) { mutableStateOf(parseSignature(value)) }
    var live by remember { mutableStateOf(emptyList<Offset>()) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { 132.dp.toPx() }
        Canvas(Modifier.fillMaxWidth().height(132.dp).padding(top = 5.dp).background(Color.White, RoundedCornerShape(10.dp))
            .pointerInput(value, widthPx, heightPx) {
                detectDragGestures(
                    onDragStart = { p -> live = listOf(Offset(p.x / widthPx, p.y / heightPx)) },
                    onDrag = { change, _ -> live = live + Offset(change.position.x / widthPx, change.position.y / heightPx) },
                    onDragEnd = { if (live.isNotEmpty()) { strokes = strokes + listOf(live); live = emptyList(); onChange(encodeSignature(strokes)) } }
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
private fun SuccessScreen(i: CasaInspection, onOpen: () -> Unit, onShare: () -> Unit, onAfter: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text("✓", fontSize = 72.sp, color = Green, fontWeight = FontWeight.Bold)
        Text("Acta generada exitosamente", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(i.propertyName, color = Muted, modifier = Modifier.padding(top = 7.dp, bottom = 18.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("▤  Ver PDF") }
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Compartir acta") }
        if (i.phase == "ANTES") OutlinedButton(onClick = onAfter, modifier = Modifier.fillMaxWidth()) { Text("Iniciar inspección Después") }
        TextButton(onClick = onHome) { Text("Volver al inicio") }
    }
}

private fun formatDate(time: Long) = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("es-SV")).format(Date(time))

private fun saveWatermarkedPhoto(context: android.content.Context, source: Bitmap, inspectionId: String, itemId: String): String {
    val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = NativeCanvas(bitmap)
    val bar = NativePaint().apply { color = android.graphics.Color.argb(180, 8, 33, 58) }
    canvas.drawRect(RectF(0f, bitmap.height - 48f, bitmap.width.toFloat(), bitmap.height.toFloat()), bar)
    val stamp = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = (bitmap.width * .035f).coerceAtLeast(16f); isFakeBoldText = true }
    canvas.drawText("CasaCheck · ${formatDate(System.currentTimeMillis())}", 12f, bitmap.height - 17f, stamp)
    val dir = File(context.filesDir, "evidence").apply { mkdirs() }
    val out = File(dir, "${inspectionId}_${itemId}.jpg")
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    bitmap.recycle()
    return out.absolutePath
}

private fun createActaPdf(context: android.content.Context, i: CasaInspection, before: CasaInspection?): File {
    val doc = PdfDocument()
    val width = 595
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(width, 842, pageNo).create())
    var canvas = page.canvas
    var y = 52f
    val title = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(18, 59, 102); textSize = 20f; isFakeBoldText = true }
    val body = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35, 48, 62); textSize = 10.5f }
    val head = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(18, 59, 102); textSize = 13f; isFakeBoldText = true }
    fun nextPage() { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(width, 842, pageNo).create()); canvas = page.canvas; y = 48f }
    fun line(value: String, paint: NativePaint = body, left: Float = 36f) {
        if (y > 775f) nextPage()
        canvas.drawText(value.take(100), left, y, paint)
        y += if (paint === head) 21f else 16f
    }
    canvas.drawText("ACTA DIGITAL DE ESTADO", 36f, y, title); y += 30f
    line(i.propertyName, head); line("${i.type} · ${i.phase} · ${formatDate(i.createdAt)}"); line(i.address)
    if (before != null) line("Comparación vinculada: Antes · ${formatDate(before.createdAt)}")
    y += 8f
    i.rooms.forEach { r ->
        line(r.name, head)
        r.items.forEach { x ->
            val info = "${x.name}: ${x.condition.label} · Cantidad ${x.quantity}" + if (x.notes.isBlank()) "" else " · ${x.notes}"
            line(info, body, 44f)
            val photo = if (x.photoPath.isNotBlank()) BitmapFactory.decodeFile(x.photoPath) else null
            if (photo != null) {
                if (y + 100 > 775f) nextPage()
                val scale = minOf(190f / photo.width, 105f / photo.height)
                val dst = RectF(44f, y, 44f + photo.width * scale, y + photo.height * scale)
                canvas.drawBitmap(photo, null, dst, null); y += dst.height() + 18f; photo.recycle()
            }
        }
    }
    nextPage()
    canvas.drawText("FIRMAS Y ACEPTACIÓN", 36f, y, title); y += 35f
    line("Propietario / encargado: ${i.ownerName}", head)
    drawSignature(canvas, i.ownerSignature, 42f, y, 220f, 85f); y += 105f
    line("Inquilino / responsable: ${i.renterName}", head)
    drawSignature(canvas, i.renterSignature, 42f, y, 220f, 85f); y += 110f
    line("Ambas partes aceptaron el estado documentado.")
    line("Generada por CasaCheck · ${formatDate(System.currentTimeMillis())}")
    doc.finishPage(page)
    val dir = File(context.filesDir, "reports").apply { mkdirs() }
    val output = File(dir, "${i.id}.pdf")
    output.outputStream().use { doc.writeTo(it) }
    doc.close()
    return output
}

private fun drawSignature(canvas: NativeCanvas, encoded: String, left: Float, top: Float, width: Float, height: Float) {
    val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(18, 59, 102); style = NativePaint.Style.STROKE; strokeWidth = 2f }
    parseSignature(encoded).forEach { points ->
        if (points.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(left + points.first().x * width, top + points.first().y * height)
            points.drop(1).forEach { path.lineTo(left + it.x * width, top + it.y * height) }
            canvas.drawPath(path, paint)
        }
    }
    canvas.drawLine(left, top + height, left + width, top + height, paint)
}
private fun parseSignature(value: String): List<List<Offset>> = value.split("|").filter { it.isNotBlank() }.map { stroke ->
    stroke.split(";").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size != 2) null else runCatching { Offset(parts[0].toFloat(), parts[1].toFloat()) }.getOrNull()
    }
}
private fun encodeSignature(strokes: List<List<Offset>>) = strokes.joinToString("|") { s -> s.joinToString(";") { "${it.x},${it.y}" } }

private fun openPdf(context: android.content.Context, path: String, share: Boolean) {
    if (path.isBlank()) return
    val file = File(path)
    if (!file.exists()) return
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = if (share) Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri) }
    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, if (share) "Compartir acta" else "Abrir acta"))
}
