package com.casacheck.app.ui.account

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val PrivacyNotice = """HomeCheck registra el nombre, correo y perfil de la cuenta, las propiedades e inspecciones que creas, fotografías, observaciones, fechas y firmas que introduces.

El acceso requiere una cuenta de Firebase. Las inspecciones y evidencias mantienen una copia local separada para cada cuenta en este dispositivo. El respaldo manual transfiere únicamente datos de propiedades, inspecciones, inventario y observaciones a Cloud Firestore del equipo. Las fotografías, los trazos de firma y los PDF permanecen en el dispositivo; no se transfieren a Firebase Storage. Firebase Authentication procesa el acceso con contraseña; HomeCheck no guarda tu contraseña en las inspecciones.

Los anuncios publican el nombre, zona, descripción y precio indicados por el propietario. Las solicitudes de alquiler solo son visibles para el solicitante y el propietario. Una invitación aceptada permite al inquilino o encargado acceder a los datos privados del espacio correspondiente. El encargado solo realiza tareas operativas en sus asignaciones y no administra alquileres ni publicaciones. Retirar el acceso impide nuevas consultas remotas; no elimina las copias ya descargadas.

Las fotografías incorporan una fecha del dispositivo. Las imágenes de galería se identifican como importadas. Las firmas se guardan como trazos dentro del acta; no se utilizan para reconocimiento biométrico. Esta versión no solicita ubicación ni acceso a contactos.

Compartir un acta abre el selector de Android. Tú eliges la aplicación y el destinatario. El PDF compartido contiene la información y evidencias de la inspección y puede conservarse fuera de HomeCheck por quien lo recibe.

Los registros locales permanecen hasta eliminar los datos o desinstalar la aplicación. Las copias respaldadas no se eliminan al desinstalar. Para consultas sobre acceso, corrección o eliminación de información del proyecto, contacta al responsable configurado por el equipo: luciavillalobos2020@gmail.com.

Revisa las evidencias y obtén el consentimiento de las partes antes de registrar sus datos y firmas."""

internal const val UsageNotice = """HomeCheck permite documentar el estado de espacios e inventario antes y después de un alquiler o evento.

El propietario registra y publica sus inmuebles y decide los vínculos de alquiler y los encargos. El inquilino envía solicitudes y acepta su alquiler. El encargado trabaja únicamente en espacios asignados. El perfil de una cuenta no otorga acceso general a inmuebles ajenos.

Registra información que estés autorizado a documentar. Revisa cantidades, estado, fotografías y observaciones antes de firmar. Cada parte debe realizar su propia firma y aceptar el estado del acta.

Los cambios destacados comparan el estado y las cantidades introducidos por el usuario. Las fotografías permiten la revisión visual; la aplicación no diagnostica daños de forma automática.

La fecha visible de una fotografía procede del dispositivo. Una imagen importada de galería se identifica como tal. La firma en pantalla consiste en trazos de aceptación; esta versión no incorpora certificados digitales ni una autoridad de sellado de tiempo.

El respaldo de datos requiere conexión y Cloud Firestore habilitado. No incluye fotografías, firmas ni PDF: expórtalos y consérvalos por separado antes de cambiar de dispositivo. La restauración recupera borradores sin evidencias ni firmas y no combina ediciones simultáneas del mismo registro.

Al compartir un acta, comprueba que el destinatario y el contenido sean correctos. El envío se realiza con la aplicación que selecciones en Android."""

@Composable
internal fun LegalNoticeDialog(kind: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (kind == "privacy") "Política de privacidad" else "Condiciones de uso") },
        text = { Text(if (kind == "privacy") PrivacyNotice else UsageNotice, modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
