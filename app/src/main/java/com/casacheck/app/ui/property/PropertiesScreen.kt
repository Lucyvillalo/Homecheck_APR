package com.casacheck.app.ui.property

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.casacheck.app.data.remote.*
import com.casacheck.app.domain.model.CasaInspection
import com.casacheck.app.domain.account.AccountRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun PropertiesScreen(inspections: List<CasaInspection>, onInspect: (PropertyRecord) -> Unit,
    onImport: (List<CasaInspection>) -> Unit, initialTab: String = "Propiedades",
    onPropertiesLoaded: (List<PropertyRecord>) -> Unit = {}) {
    val repo = remember { PropertyRepository() }
    val staffRepo = remember { StaffAssignmentRepository() }
    var assignments by remember { mutableStateOf(emptyList<StaffAssignment>()) }
    var legacy by remember { mutableStateOf(emptyList<PropertyRecord>()) }
    var staffInvitation by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<StaffAssignment?>(null) }
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf("") }
    var properties by remember { mutableStateOf(emptyList<PropertyRecord>()) }
    var listings by remember { mutableStateOf(emptyList<RentalListing>()) }
    var applications by remember { mutableStateOf(emptyList<RentalApplication>()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Casa") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var publishing by remember { mutableStateOf<PropertyRecord?>(null) }
    var zone by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val accountRole = role.takeIf { it.isNotEmpty() }?.let(AccountRole::fromStored)
    val owner = accountRole == AccountRole.OWNER
    val staff = accountRole == AccountRole.STAFF
    suspend fun refresh() {
        role = repo.role()
        properties = repo.properties()
        onPropertiesLoaded(properties)
        val currentRole = AccountRole.fromStored(role)
        if (currentRole == AccountRole.STAFF) {
            listings = emptyList(); applications = emptyList(); assignments = emptyList()
            legacy = staffRepo.legacyProperties().filter { p -> properties.none { it.id == p.id } }
            tab = "Propiedades"
        } else {
            listings = repo.listings()
            applications = repo.applications(currentRole == AccountRole.OWNER)
            assignments = if (currentRole == AccountRole.OWNER) staffRepo.ownedAssignments() else emptyList()
            legacy = emptyList()
        }
        loaded = true
    }
    fun execute(success: String = "", action: suspend () -> Unit) {
        if (busy) return
        busy = true; message = ""; failed = false
        scope.launch {
            try { action(); refresh(); message = success }
            catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                failed = true; message = "Firebase tardó demasiado en responder. Revisa Internet y pulsa Actualizar antes de repetir la operación."
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failed = true
                message = when (e) {
                    is com.google.firebase.firestore.FirebaseFirestoreException -> when(e.code) {
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firebase rechazó la operación. Comprueba que la cuenta tenga permiso y que las reglas de propiedades estén publicadas."
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> "No se pudo conectar con Firebase. Revisa Internet y vuelve a intentar."
                        else -> "No se pudo completar la operación en Firebase (${e.code}). Vuelve a intentar."
                    }
                    is IllegalArgumentException, is IllegalStateException -> e.message ?: "Revisa los datos ingresados."
                    else -> "No se pudo completar la operación. Revisa la conexión y vuelve a intentar."
                }
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { execute { } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(accountRole?.propertyTitle ?: "Cargando tu espacio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (role.isNotEmpty()) Text("Perfil de la cuenta: $role")
        Text(when(accountRole) {
            AccountRole.OWNER -> "Administra tus inmuebles, publicaciones, solicitudes e invitaciones. Solo tú decides los vínculos de alquiler y los encargos."
            AccountRole.TENANT -> "Busca un alquiler, consulta tus solicitudes y acepta el vínculo con el propietario."
            AccountRole.STAFF -> "Trabaja únicamente en los espacios que te asigne un propietario: inspecciones, inventario e incidencias."
            null -> "Comprobando el perfil de la cuenta."
        })
        Text("Fotos, firmas y PDF permanecen en el teléfono; se comparten únicamente los datos de las inspecciones.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (accountRole == null) emptyList() else if (staff) listOf("Propiedades") else listOf("Propiedades", "Alquileres", "Solicitudes")).forEach { key ->
                val label = when(key) {
                    "Propiedades" -> accountRole?.propertyTitle ?: "Propiedades"
                    "Alquileres" -> if (owner) "Mis anuncios" else "Buscar alquiler"
                    else -> if (owner) "Solicitudes recibidas" else "Mis solicitudes"
                }
                FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(label) })
            }
        }
        OutlinedButton(onClick = { execute { } }, enabled = !busy) { Text("Actualizar") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (!loaded) return@Column
        when(tab) {
            "Propiedades" -> {
                if (owner) Button(onClick = { creating = true }, enabled = !busy) { Text("Registrar propiedad") }
                if (!owner) Text(if (staff) "Pide al propietario un código de encargado; una invitación de inquilino no concede permisos operativos." else "Pide al propietario una invitación de inquilino para vincular tu alquiler.")
                if (!owner) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código de invitación") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = { execute("Propiedad vinculada a tu cuenta.") { if (staff) staffRepo.accept(code) else repo.accept(code); code = "" } }, enabled = !busy && code.isNotBlank()) { Text(if (staff) "Aceptar encargo" else "Aceptar invitación de inquilino") }
                }
                if (properties.isEmpty()) Text(if (staff) "No tienes espacios asignados. El propietario debe invitarte antes de crear inspecciones." else "Todavía no tienes propiedades vinculadas.")
                if (staff && legacy.isNotEmpty()) {
                    Text("Registros de una versión anterior", fontWeight = FontWeight.Bold)
                    Text("Se conservan como historial. No conceden permisos de propietario ni una asignación operativa.")
                    legacy.forEach { Text(it.name + " · " + it.address) }
                }
                properties.forEach { p ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(p.name, fontWeight = FontWeight.Bold); Text("${p.type} · ${p.address}")
                            Text(if (staff) "Encargo activo · inspecciones e inventario" else if (p.tenantUid.isEmpty()) "Sin inquilino vinculado" else "Inquilino vinculado")
                            Button(onClick = { onInspect(p) }, enabled = !busy) { Text("Crear inspección de esta propiedad") }
                            if (inspections.any { it.propertyId == p.id }) OutlinedButton(onClick = { execute("Datos compartidos. Las fotos, firmas y PDF siguen solo en este teléfono.") { repo.share(p, inspections) } }, enabled = !busy) { Text("Compartir datos de inspecciones") }
                            OutlinedButton(onClick = { execute("Se importaron borradores sin fotos ni firmas. Se conservaron tus registros existentes.") { onImport(repo.shared(p)) } }, enabled = !busy) { Text("Recibir inspecciones compartidas") }
                            if (owner && p.ownerUid == uid) {
                                OutlinedButton(onClick = { publishing = p; zone = ""; description = ""; price = "" }, enabled = !busy && p.tenantUid.isBlank()) { Text("Publicar o actualizar anuncio") }
                                if (p.tenantUid.isNotBlank()) Text("Alquiler vinculado: el anuncio se retira al aceptar y no se vuelve a publicar mientras esté ocupado.")
                                if (listings.any { it.id == p.id }) TextButton(onClick = { execute("Anuncio retirado del catálogo.") { repo.withdraw(p) } }, enabled = !busy) { Text("Retirar anuncio") }
                                if (p.tenantUid.isEmpty()) OutlinedButton(onClick = { execute { invitation = repo.invite(p); staffInvitation = false } }, enabled = !busy) { Text("Invitar inquilino") }
                                else TextButton(onClick = { execute("Acceso del inquilino revocado. Las copias que ya descargó permanecen en su teléfono.") { repo.unlink(p) } }, enabled = !busy) { Text("Desvincular inquilino") }
                                OutlinedButton(onClick = { execute { invitation = staffRepo.invite(p); staffInvitation = true } }, enabled = !busy) { Text("Invitar encargado") }
                                assignments.filter { it.propertyId == p.id }.forEach { a ->
                                    Text("Encargado: ${a.staffName.ifBlank { a.staffUid.takeLast(8) }}")
                                    TextButton(onClick = { revoking = a }, enabled = !busy) { Text("Retirar acceso del encargado") }
                                }
                            }
                        }
                    }
                }
            }
            "Alquileres" -> {
                Text("Hasta 100 anuncios activos. Las solicitudes no son reservas ni pagos.")
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar por nombre o zona") }, modifier = Modifier.fillMaxWidth())
                val matches = listings.filter { (!owner || it.ownerUid == uid) && (it.title + " " + it.zone).contains(query, ignoreCase = true) }
                if (matches.isEmpty()) Text("No hay anuncios que coincidan con la búsqueda.")
                matches.forEach { l -> OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(l.title, fontWeight = FontWeight.Bold); Text(l.zone); Text(l.description)
                        Text(String.format(Locale.US, "$%.2f USD / mes", l.priceCents / 100.0))
                        if (accountRole == AccountRole.TENANT && l.ownerUid != uid) Button(onClick = { execute("Solicitud enviada. Consulta su estado en Solicitudes.") { repo.apply(l) } }, enabled = !busy) { Text("Solicitar alquiler") }
                    }
                } }
            }
            "Solicitudes" -> {
                if (applications.isEmpty()) Text("Todavía no hay solicitudes.")
                applications.forEach { a -> OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val propertyName = a.propertyName.ifBlank { properties.firstOrNull { it.id == a.propertyId }?.name ?: listings.firstOrNull { it.id == a.propertyId }?.title ?: "Anuncio retirado" }
                        Text(propertyName, fontWeight = FontWeight.Bold)
                        if (owner) Text("Solicitante: ${a.applicantName.ifBlank { "Cuenta sin nombre" }}")
                        Text(if (!owner && properties.any { it.id == a.propertyId && it.tenantUid == uid }) "Vinculación de alquiler activa" else when(a.status) { "approved" -> "Aprobada: invitación disponible"; "declined" -> "Rechazada"; else -> "Pendiente" })
                        if (owner && a.status == "pending") {
                            val p = properties.firstOrNull { it.id == a.propertyId }
                            if (p != null) Button(onClick = { execute("Solicitud aprobada. El inquilino puede aceptar la vinculación desde su cuenta.") { repo.invite(p, a) } }, enabled = !busy && p.tenantUid.isEmpty()) { Text("Aprobar e invitar") }
                            TextButton(onClick = { execute("Solicitud rechazada.") { repo.decline(a) } }, enabled = !busy) { Text("Rechazar solicitud") }
                        }
                        if (!owner && a.status == "approved" && properties.none { it.id == a.propertyId && it.tenantUid == uid }) Button(onClick = { execute("Vinculación aceptada. Abre Propiedades para inspeccionar el espacio.") { repo.accept(a.inviteCode) } }, enabled = !busy) { Text("Aceptar vinculación") }
                    }
                } }
            }
        }
    }
    if (invitation.isNotEmpty()) AlertDialog(onDismissRequest = { invitation = "" }, title = { Text(if (staffInvitation) "Invitación de encargado" else "Invitación de inquilino") }, text = {
        Column { Text(if (staffInvitation) "Entrega este código a una cuenta Encargado. Caduca en siete días y sustituye al código de encargo pendiente anterior, sin retirar los encargos ya aceptados. Permite revisar el espacio y trabajar en inspecciones e inventario; no permite publicar ni administrar alquileres. Puedes retirar el acceso desde la propiedad." else "Entrega este código únicamente al inquilino. Caduca en siete días y sustituye la invitación de inquilino anterior. Quien lo acepte podrá leer los datos privados de esta propiedad.")
            androidx.compose.foundation.text.selection.SelectionContainer { Text(invitation) } }
    }, confirmButton = { TextButton(onClick = { invitation = "" }) { Text("Cerrar") } })
    revoking?.let { assignment -> AlertDialog(onDismissRequest = { if (!busy) revoking = null },
        title = { Text("Retirar acceso del encargado") }, text = { Text("Dejará de acceder a esta propiedad y sus datos compartidos. Las copias que ya descargó permanecen en su teléfono. No cambia el vínculo del inquilino. También se anulan los códigos de encargo pendientes.") },
        confirmButton = { TextButton(enabled = !busy,onClick = { execute("Acceso operativo retirado.") { staffRepo.revoke(assignment); revoking = null } }) { Text("Retirar acceso") } },
        dismissButton = { TextButton(enabled = !busy,onClick = { revoking = null }) { Text("Cancelar") } }) }
    if (creating) AlertDialog(onDismissRequest = { if (!busy) creating = false }, title = { Text("Registrar propiedad privada") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
            OutlinedTextField(address, { address = it }, label = { Text("Dirección privada") })
            listOf("Casa", "Apartamento", "Salón").forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
            if (failed) Text(message, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = { execute("Propiedad registrada.") { repo.create(name, address, type); creating = false; name = ""; address = "" } }) { Text("Guardar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { creating = false }) { Text("Cancelar") } })
    publishing?.let { p -> AlertDialog(onDismissRequest = { if (!busy) publishing = null }, title = { Text("Publicar ${p.name}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Se publicarán el nombre, la zona, la descripción y el precio. Evita incluir datos personales o la dirección exacta. El anuncio no incluye fotografías.")
            OutlinedTextField(zone, { zone = it }, label = { Text("Zona pública") })
            OutlinedTextField(description, { description = it }, label = { Text("Descripción pública") })
            OutlinedTextField(price, { price = it }, label = { Text("Precio mensual USD") }, singleLine = true)
            if (failed) Text(message, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = { execute("Anuncio publicado en el catálogo.") { repo.publish(p, zone, description, price); publishing = null } }) { Text("Publicar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { publishing = null }) { Text("Cancelar") } }) }
}
