package com.casacheck.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casacheck.app.R
import com.casacheck.app.data.evidence.saveEvidence
import com.casacheck.app.data.local.CasaStore
import com.casacheck.app.data.remote.CloudInspectionRepository
import com.casacheck.app.data.remote.FirebaseServices
import com.casacheck.app.data.remote.PropertyRepository
import com.casacheck.app.data.remote.PropertyRecord
import com.casacheck.app.domain.account.AccountRole
import com.casacheck.app.ui.account.AccountWorkspace
import com.casacheck.app.data.report.createActaPdf
import com.casacheck.app.domain.missingRequirements
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.model.CasaItem
import com.casacheck.app.domain.model.CasaPhoto
import com.casacheck.app.domain.model.CasaRoom
import com.casacheck.app.domain.newAfter
import com.casacheck.app.domain.roomsFor
import com.casacheck.app.platform.openPdf
import com.casacheck.app.ui.property.PropertiesScreen
import com.casacheck.app.ui.account.IncomingRecoveryLink
import com.casacheck.app.ui.account.PasswordRecoveryScreen
import com.casacheck.app.ui.account.AccountScreen
import com.casacheck.app.ui.account.RoleSelectionScreen
import com.casacheck.app.ui.components.HomeCheckIcon
import com.casacheck.app.ui.inspection.ActaPreviewScreen
import com.casacheck.app.ui.inspection.ChecklistScreen
import com.casacheck.app.ui.inspection.ComparisonScreen
import com.casacheck.app.ui.inspection.CreateScreen
import com.casacheck.app.ui.inspection.HomeScreen
import com.casacheck.app.ui.inspection.InspectionList
import com.casacheck.app.ui.inspection.InventoryScreen
import com.casacheck.app.ui.inspection.ItemScreen
import com.casacheck.app.ui.inspection.RoomsScreen
import com.casacheck.app.ui.inspection.SummaryScreen
import com.casacheck.app.ui.report.SignatureScreen
import com.casacheck.app.ui.report.SuccessScreen
import com.casacheck.app.ui.theme.Blue
import com.casacheck.app.ui.theme.HomeTypography
import com.casacheck.app.ui.theme.Muted
import com.casacheck.app.ui.theme.Navy
import com.casacheck.app.ui.theme.Pale
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun HomeCheckApp(incomingRecovery: IncomingRecoveryLink? = null, onRecoveryHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val configured = remember { FirebaseServices.initialize(context) }
    var account by remember { mutableStateOf(if (configured) FirebaseAuth.getInstance().currentUser?.uid else null) }
    var loginEmail by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf("") }
    MaterialTheme(typography = HomeTypography, colorScheme = lightColorScheme(primary = Navy, secondary = Blue, background = Pale, surface = Color.White, secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8F0F8), onSecondaryContainer = Navy)) {
        if (incomingRecovery != null) key(incomingRecovery.id) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
                    PasswordRecoveryScreen(configured, "", onBack = { onRecoveryHandled() }, onResetComplete = { recoveredEmail ->
                        account = null; loginEmail = recoveredEmail
                        if (selectedRole.isBlank()) selectedRole = "Inquilino"
                        onRecoveryHandled()
                    }, initialLink = incomingRecovery.url)
                }
            }
        }
        else if (account == null && selectedRole.isBlank()) RoleSelectionScreen { selectedRole = it }
        else if (account == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 560.dp).fillMaxHeight()) { AccountScreen(configured, { account = FirebaseAuth.getInstance().currentUser?.uid }, selectedRole, loginEmail) }
        }
        else key(account) { AccountWorkspace(account!!) {
            if (configured) FirebaseAuth.getInstance().signOut()
            account = null
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CasaCheckApp(account: String, role: AccountRole = AccountRole.TENANT, onLogout: () -> Unit) {
    val context = LocalContext.current
    val loaded = remember { runCatching { CasaStore.load(context, account) } }
    if (loaded.isFailure) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("No se pudieron leer las inspecciones locales.", fontSize = 22.sp)
            Text("Los datos originales se conservaron. No se guardarán cambios sobre ellos. Revisa el almacenamiento antes de volver a intentar.")
            TextButton(onClick = onLogout) { Text("Volver al acceso") }
        }
        return
    }
    var inspections by remember { mutableStateOf(loaded.getOrThrow()) }
    var screen by rememberSaveable { mutableStateOf("inicio") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var selectedRoom by rememberSaveable { mutableStateOf("") }
    var selectedItem by rememberSaveable { mutableStateOf("") }
    var createType by remember { mutableStateOf("Casa") }
    var propertyTab by rememberSaveable { mutableStateOf("Propiedades") }
    var assignedProperties by remember { mutableStateOf(emptyList<PropertyRecord>()) }
    var checkingAssignments by remember { mutableStateOf(role.requiresAssignment) }
    val persistenceLock = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var working by remember { mutableStateOf(false) }
    fun notify(message: String) { scope.launch { snackbar.showSnackbar(message) } }
    LaunchedEffect(inspections) {
        try { persistenceLock.withLock { withContext(Dispatchers.IO) { CasaStore.save(context, inspections, account) } } }
        catch (e: Exception) { snackbar.showSnackbar("No se pudo guardar: revisa el almacenamiento disponible.") }
    }
    LaunchedEffect(role) {
        if (role.requiresAssignment) {
            checkingAssignments = true
            try { assignedProperties = PropertyRepository().properties() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { notify("No se pudieron comprobar tus encargos. Abre Espacios asignados y actualiza con conexión.") }
            finally { checkingAssignments = false }
        }
    }
    val visibleInspections = if (role.requiresAssignment) inspections.filter { i -> assignedProperties.any { it.id == i.propertyId } } else inspections
    fun mayEdit(inspection: CasaInspection) = !role.requiresAssignment || assignedProperties.any { it.id == inspection.propertyId }
    fun startInspection() { propertyTab = "Propiedades"; screen = if (role.requiresAssignment) "propiedades" else "crear" }
    val current = inspections.firstOrNull { it.id == selectedId }
    val room = current?.rooms?.firstOrNull { it.id == selectedRoom }
    val item = room?.items?.firstOrNull { it.id == selectedItem }

    fun updateInspection(updated: CasaInspection) {
        if (!mayEdit(updated)) { notify("Necesitas un encargo activo para modificar esta inspección."); return }
        inspections = inspections.map { if (it.id == updated.id) updated else it }
    }
    fun changeItem(updated: CasaItem) {
        val i = current ?: return
        val r = room ?: return
        updateInspection(i.copy(rooms = i.rooms.map { a -> if (a.id == r.id) a.copy(items = a.items.map { x -> if (x.id == updated.id) updated else x }) else a }))
    }
    fun back() {
        if (working) return
        screen = when (screen) {
            "crear", "actas", "inspecciones", "perfil", "inventario" -> "inicio"
            "ambientes" -> "inspecciones"
            "checklist" -> "ambientes"
            "elemento" -> "checklist"
            "resumen", "comparar" -> "ambientes"
            "acta" -> "resumen"
            "firmas" -> "acta"
            "exito" -> "actas"
            else -> "inicio"
        }
    }
    BackHandler(enabled = screen != "inicio") { back() }

    MaterialTheme(typography = HomeTypography, colorScheme = lightColorScheme(primary = Navy, secondary = Blue, background = Pale, surface = Color.White, secondaryContainer = Color(0xFFE8F0F8), onSecondaryContainer = Navy)) {
        Scaffold(
            containerColor = Pale,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            if (screen == "inicio") Image(painterResource(R.drawable.homecheck_logo), "HomeCheck", Modifier.width(125.dp).height(48.dp))
                            else Text(titleFor(screen), fontWeight = FontWeight.Bold, color = Navy, fontSize = 20.sp)
                            if (current != null && screen !in listOf("inicio", "crear", "actas", "inspecciones")) Text("${current.propertyName} · ${current.phase}", fontSize = 12.sp, color = Muted)
                        }
                    },
                    navigationIcon = { if (screen != "inicio") TextButton(onClick = { back() }) { Text("‹", fontSize = 26.sp, color = Navy) } },
                    actions = { if (screen == "inicio") TextButton(enabled = !working, onClick = { screen = "propiedades" }) { Text(if (role.requiresAssignment) "Mis encargos" else "Propiedades") }; TextButton(enabled = !working, onClick = { screen = "perfil" }) { Text("Cuenta") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                if (screen in listOf("inicio", "inspecciones", "actas", "inventario")) NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(selected = screen == "inicio", onClick = { screen = "inicio" }, icon = { HomeCheckIcon("home", "Inicio", Modifier.size(24.dp)) }, label = { Text("Inicio") })
                    NavigationBarItem(selected = screen == "inspecciones", onClick = { screen = "inspecciones" }, icon = { HomeCheckIcon("checklist", "Inspecciones", Modifier.size(24.dp)) }, label = { Text("Inspecciones") })
                    NavigationBarItem(selected = screen == "actas", onClick = { screen = "actas" }, icon = { HomeCheckIcon("document", "Actas", Modifier.size(24.dp)) }, label = { Text("Actas") })
                    NavigationBarItem(selected = screen == "inventario", onClick = { screen = "inventario" }, icon = { HomeCheckIcon("inventory", "Inventario", Modifier.size(24.dp)) }, label = { Text("Inventario") })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
              Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                val editorScreens = setOf("ambientes","checklist","elemento","resumen","comparar","acta","firmas")
                if (role.requiresAssignment && screen in editorScreens && current != null && !mayEdit(current)) {
                    Column(Modifier.padding(24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (checkingAssignments) CircularProgressIndicator()
                        Text("Esta inspección necesita una asignación activa del propietario. Los datos guardados se conservan.")
                        Button(onClick = { screen = "propiedades" }) { Text("Ver mis encargos") }
                    }
                } else when (screen) {
                    "inicio" -> HomeScreen(visibleInspections, { startInspection() }, { i -> selectedId = i.id; screen = if (i.completed) "exito" else "ambientes" }, { screen = "inspecciones" }, { screen = "actas" }, { screen = "inventario" }, FirebaseAuth.getInstance().currentUser?.displayName.orEmpty(), role) { tab -> propertyTab = tab; screen = "propiedades" }
                    "perfil" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(FirebaseAuth.getInstance().currentUser?.displayName ?: "Mi cuenta", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(FirebaseAuth.getInstance().currentUser?.email.orEmpty())
                        Text("Perfil verificado: ${role.label}")
                        if (role.requiresAssignment) {
                            Text("Tus permisos operativos dependen de los encargos activos del propietario. Las actas que ya guardaste localmente se conservan.")
                            OutlinedButton(onClick = { screen = "archivo" }) { Text("Archivo de actas locales") }
                        }
                        if (!role.requiresAssignment) {
                            Text("Firebase guarda únicamente los datos de las inspecciones. Fotos, firmas y PDF permanecen en este teléfono: no se respaldan en la nube. La restauración agrega borradores nuevos sin evidencias ni firmas y conserva los registros locales existentes.")
                            Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = {
                                working = true
                                scope.launch { try {
                                    CloudInspectionRepository(account).upload(inspections)
                                    notify("Datos respaldados en Firestore. Fotos, firmas y PDF siguen solo en este teléfono.")
                                } catch (e: Exception) { notify("No se pudo sincronizar. Revisa la conexión y las reglas de Firebase.") }
                                finally { working = false } }
                            }, enabled = !working) { Text("Respaldar datos en Firestore") }
                            OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = {
                                working = true
                                scope.launch { try {
                                    val remote = CloudInspectionRepository(account).download()
                                    inspections = inspections + remote.distinctBy { it.id }.filter { r -> inspections.none { it.id == r.id } }
                                    notify("Datos restaurados como borradores sin fotos ni firmas. Se conservaron los registros locales existentes.")
                                } catch (e: Exception) { notify("No se pudo restaurar. Revisa la conexión y las reglas de Firebase.") }
                                finally { working = false } }
                            }, enabled = !working) { Text("Restaurar datos de Firestore") }
                        }
                        if (working) CircularProgressIndicator()
                        OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = onLogout, enabled = !working) { Text("Cerrar sesión") }
                    }
                    "propiedades" -> PropertiesScreen(inspections, { p ->
                        if (role.requiresAssignment && assignedProperties.none { it.id == p.id }) { notify("El espacio requiere una asignación activa."); return@PropertiesScreen }
                        val created = CasaInspection(propertyId = p.id, propertyName = p.name, address = p.address, type = p.type, rooms = roomsFor(p.type))
                        inspections = inspections + created; selectedId = created.id; screen = "ambientes"
                    }, { remote -> inspections = inspections + remote.distinctBy { it.id }.filter { r -> inspections.none { it.id == r.id } } }, propertyTab, { properties -> if (role.requiresAssignment) assignedProperties = properties })
                    "inventario" -> InventoryScreen(visibleInspections)
                    "archivo" -> InspectionList(inspections.filter { it.completed }, true, { i -> selectedId = i.id; screen = "exito" }, { startInspection() })
                    "crear" -> if (role.requiresAssignment) {
                        Column(Modifier.padding(24.dp)) { Text("Elige uno de tus espacios asignados para iniciar una inspección."); Button(onClick = { screen = "propiedades" }) { Text("Ver espacios asignados") } }
                    } else CreateScreen(createType, { createType = it }) { name, address, type ->
                        val created = CasaInspection(propertyName = name, address = address, type = type, rooms = roomsFor(type))
                        inspections = inspections + created; selectedId = created.id; screen = "ambientes"
                    }
                    "inspecciones" -> InspectionList(visibleInspections.filter { !it.completed }, false, { i -> selectedId = i.id; screen = "ambientes" }, { startInspection() })
                    "actas" -> InspectionList(visibleInspections.filter { it.completed }, true, { i -> selectedId = i.id; screen = "exito" }, { startInspection() })
                    "ambientes" -> current?.let { inspection ->
                        RoomsScreen(inspection, { selectedRoom = it.id; screen = "checklist" }, { screen = "resumen" }) { name ->
                            val template = roomsFor(inspection.type).first()
                            updateInspection(inspection.copy(rooms = inspection.rooms + CasaRoom(name = name, items = template.items.map { it.copy(id = java.util.UUID.randomUUID().toString()) })))
                        }
                    }
                    "checklist" -> if (current != null && room != null) ChecklistScreen(room, current.phase, { selectedItem = it.id; screen = "elemento" }) {
                        val idx = current.rooms.indexOfFirst { it.id == room.id }
                        if (idx + 1 < current.rooms.size) selectedRoom = current.rooms[idx + 1].id else screen = "ambientes"
                    }
                    "elemento" -> if (current != null && room != null && item != null) ItemScreen(item, { source, origin ->
                        working = true
                        scope.launch {
                            try {
                                val capturedAt = System.currentTimeMillis()
                                val path = withContext(Dispatchers.IO) { saveEvidence(context, source, current.id, item.id, capturedAt, origin) }
                                changeItem(item.copy(photoPath = path, capturedAt = capturedAt, photoOrigin = origin, extraPhotos = item.extraPhotos + if (item.photoPath.isBlank()) emptyList() else listOf(CasaPhoto(item.photoPath, item.capturedAt, item.photoOrigin))))
                            } catch (e: Exception) { notify("No se pudo guardar la fotografía. Intenta nuevamente.") }
                            finally { source.delete(); working = false }
                        }
                    }, { updated -> changeItem(updated); screen = "checklist" }, working)
                    "resumen" -> current?.let { SummaryScreen(it, inspections.firstOrNull { b -> b.id == it.baselineId }, { screen = "acta" }, { screen = "comparar" }) }
                    "comparar" -> current?.let { ComparisonScreen(it, inspections.firstOrNull { b -> b.id == it.baselineId }) }
                    "acta" -> current?.let { inspection -> ActaPreviewScreen(inspection, inspections.firstOrNull { it.id == inspection.baselineId }) { screen = "firmas" } }
                    "firmas" -> current?.let { inspection -> SignatureScreen(inspection, working) { owner, renter, ownerSign, renterSign ->
                        val signed = inspection.copy(ownerName = owner, renterName = renter, ownerSignature = ownerSign, renterSignature = renterSign)
                        if (missingRequirements(signed).isEmpty() && !working) {
                            working = true
                            scope.launch {
                                try {
                                    val pdf = withContext(Dispatchers.IO) { createActaPdf(context, signed, inspections.firstOrNull { it.id == signed.baselineId }) }
                                    val finished = signed.copy(completed = true, reportPath = pdf.absolutePath)
                                    val updated = inspections.map { if (it.id == finished.id) finished else it }
                                    persistenceLock.withLock { withContext(Dispatchers.IO) { CasaStore.save(context, updated, account) } }
                                    inspections = updated; screen = "exito"
                                } catch (e: Exception) { notify("No se pudo generar el acta. Revisa el almacenamiento e intenta de nuevo.") }
                                finally { working = false }
                            }
                        }
                    } }
                    "exito" -> current?.let { inspection -> SuccessScreen(inspection, { runCatching { openPdf(context, inspection.reportPath, false) }.onFailure { notify("No se pudo abrir el PDF. Instala un visor o vuelve a generar el acta.") } }, { runCatching { openPdf(context, inspection.reportPath, true) }.onFailure { notify("No se pudo compartir el acta.") } }, {
                        if (!mayEdit(inspection)) { notify("Para crear una devolución necesitas una asignación activa."); return@SuccessScreen }
                        val next = inspections.firstOrNull { it.baselineId == inspection.id && !it.completed } ?: newAfter(inspection).also { inspections = inspections + it }
                        selectedId = next.id; screen = "ambientes"
                    }, { screen = "inicio" }) }
                }
              }
            }
        }
    }
}

private fun titleFor(screen: String) = when (screen) {
    "archivo" -> "Archivo local"; "propiedades" -> "Propiedades"; "perfil" -> "Mi cuenta"; "inventario" -> "Inventario"; "crear" -> "Tipo de inspección"; "inspecciones" -> "Inspecciones"; "actas" -> "Mis actas"
    "ambientes" -> "Ambientes"; "checklist" -> "Checklist"; "elemento" -> "Estado del elemento"
    "acta" -> "Generar acta"; "resumen" -> "Resumen de inspección"; "comparar" -> "Comparación"; "firmas" -> "Firmas"; else -> "Acta generada"
}
