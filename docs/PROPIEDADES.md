# Propiedades, inquilinos y anuncios

Ampliación solicitada por la usuaria el 28 de octubre de 2026. El documento original de negocio no define un marketplace: este alcance se añadió expresamente después.

## Recorridos

Desde Inicio → Propiedades:

- Propietario: registrar una propiedad privada, crear inspecciones con su ID estable, publicar/retirar el anuncio mensual en USD y revisar solicitudes. Aprobar una solicitud genera una invitación destinada exclusivamente al UID solicitante.
- Encargado: acepta una invitación operativa y trabaja únicamente en espacios asignados; no publica ni administra alquileres. Ver ROLES.md.
- Inquilino: consultar hasta 100 anuncios activos, buscar por nombre/zona, enviar una solicitud y aceptar la invitación aprobada desde Solicitudes.
- También es posible vincular una propiedad sin anuncio mediante un código aleatorio de 128 bits, válido siete días. Se muestra para que el propietario lo entregue al destinatario. Solo se almacena su hash SHA-256 en la colección de invitaciones. Una invitación nueva sustituye a la anterior y no admite múltiples inquilinos simultáneos.
- Los miembros comparten manualmente los datos de las inspecciones de esa propiedad. Al aceptar el alquiler, el anuncio se retira atómicamente y no admite nuevas solicitudes mientras haya inquilino. Cada autor conserva sus documentos; otro miembro no puede sobrescribirlos. La recepción agrega borradores sin reemplazar registros locales existentes.
- Desvincular revoca el acceso remoto del inquilino y a nuevas descargas. No elimina las copias que ya descargó.

## Privacidad y límites

Los anuncios exponen nombre, zona, descripción y precio. No contienen dirección privada, miembros, fotos, firmas, inventario ni PDF. El propietario debe evitar datos personales en el texto libre. Las solicitudes y propiedades son privadas. La autoría de una inspección no demuestra propiedad legal.

Fotos, trazos de firma y PDF permanecen en el teléfono; no hay Storage ni facturación. Para entregar el acta completa se usa Compartir PDF. Importar datos compartidos no transmite fotos ni genera una firma remota. Las firmas originales siguen siendo presenciales en un mismo dispositivo. No se implementan pagos, reservas ni contratos de alquiler. Las cuentas conservan el rol registrado; el portal de login no cambia los permisos del perfil existente.

La sincronización es manual y por autor; no mezcla cambios simultáneos. Las inspecciones antiguas se conservan como registros personales, sin inferir que su creador sea propietario. Para vincular inspecciones nuevas a una propiedad real, iniciarlas desde su tarjeta.

## Activación

Las reglas de firebase/firestore.rules se compilaron y publicaron correctamente en kotlin-firebase-1ae97 el 28/10/2026. Para volver a publicarlas después de cambios verificados:

```powershell
firebase deploy --only firestore:rules --project kotlin-firebase-1ae97
```

No cambiar las reglas a acceso global para resolver un error de permisos.

## Pruebas reproducibles

Node.js con Firebase CLI, Java 21 y Python 3; proyecto demo aislado, sin cuentas reales:

```powershell
firebase emulators:start --only auth,firestore --project demo-homecheck-reset --config firebase/firebase.emulators.json
```

En otra terminal:

```powershell
python scripts/test-property-rules.py
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.casacheck.app.PropertyFlowEmulatorTest -e propertiesEmulator true com.casacheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

La prueba Android se ejecuta en un proceso independiente de RecoveryIntentTest porque ambas inicializan Firebase por defecto con datos de prueba.

## Delegación operativa

El encargado dejó de compartir los permisos del propietario. Sus asignaciones están en staffAssignments y se aceptan mediante staffInvites. El propietario puede revocarlas sin desvincular al inquilino. Ver ROLES.md para la demostración de los tres recorridos y los límites de archivos locales.
