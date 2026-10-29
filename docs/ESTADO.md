# Estado de HomeCheck APR

Actualizado el 28/10/2026.

## Implementación

- Android Kotlin/Compose, logo y recursos originales, tipografía Inter y licencia incluida.
- Registro, login obligatorio, errores detallados, fortaleza de contraseña y recuperación dentro de la app.
- Inspecciones residenciales/eventos, ambientes, inventario, filtros y persistencia por cuenta.
- Cámara/galería, hasta ocho fotos por elemento, fecha/origen, validaciones y comparación Antes/Después.
- Actas PDF con evidencias y firmas presenciales en un mismo dispositivo.
- Propietario: inmuebles propios, anuncios disponibles, solicitudes e invitaciones de alquiler y encargo.
- Inquilino: búsqueda, solicitudes y aceptación del alquiler; sin publicación ni administración.
- Encargado: inspecciones únicamente en espacios asignados; sin facultades de propietario.
- Aceptar un alquiler retira el anuncio en la misma transacción; un inmueble ocupado no admite otro alquiler.
- El perfil procede de la cuenta autenticada, no del portal seleccionado al iniciar sesión.
- Intercambio manual de datos de inspecciones entre miembros; autoría protegida y restauración como borradores.

## Backend publicado

Proyecto kotlin-firebase-1ae97; app com.casacheck.app. Authentication y Firestore, sin Storage ni activación de facturación.

Las reglas de los tres roles están publicadas y la lectura autenticada confirmó que coinciden exactamente con la fuente probada. Ver verification/firebase-deployment-results.txt. Las huellas Android y la asociación HTTPS están publicadas. Google Digital Asset Links confirmó linked=true y Android 12 confirmó verified. Ambos formatos de enlace abrieron MainActivity mediante ACTION_VIEW sin forzar el paquete. La página web de continuación responde HTTP 200.

## Verificación

La separación actual de roles aprobó 99 comprobaciones de permisos en emuladores oficiales y 12 pruebas unitarias. Los cuatro recorridos Android de roles aprobaron. También aprobaron una regresión de propiedades, dos de enlaces de recuperación, seis de recuperación/acceso y una de creación de inspección: 14 pruebas Android locales, sin fallos. Dos pruebas adicionales contra Firebase real aprobaron registro/login, correo duplicado y rechazo explícito de un código de recuperación inválido: 16 pruebas Android en esta ejecución. Se usaron cuentas ficticias, sin correos a personas reales. El cambio válido de contraseña se probó con Auth Emulator oficial y SDK Android. Compilación aprobada; lint: 0 errores y 6 advertencias.

También se conservan las evidencias previas de cámara, inventario, PDF, firmas y tamaños móvil/tableta. Los resultados están organizados en verification/README.md.

## Límites acordados

- Fotos, trazos de firma y PDF permanecen locales. Conservar los PDF antes de borrar datos o cambiar de teléfono.
- El respaldo y el intercambio son manuales, sin combinación de ediciones simultáneas. La recepción conserva los registros locales existentes.
- Revocar un vínculo retira el acceso remoto; no borra copias previamente descargadas.
- No hay pagos, reservas ni firma certificada. La comparación fotográfica es visual y el sello utiliza el reloj del dispositivo.
- La apertura de enlaces se comprobó en Android 12. La entrega a un buzón personal y la compatibilidad con todos los teléfonos no se han verificado.

## Entrega de carpeta

Código, configuración, recursos, pruebas, documentación y APK 1.2 verificado. La entrega conserva el APK y las evidencias útiles; las cachés se regeneran. El historial Git y la configuración cliente Firebase se conservan.

Abrir la raíz en Android Studio; instrucciones en README.md y CONTRIBUIR.md. Backend y permisos en ARQUITECTURA.md y PROPIEDADES.md; recuperación en RECUPERACION.md.
