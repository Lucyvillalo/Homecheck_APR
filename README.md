# HomeCheck APR

Aplicación Android en Kotlin y Jetpack Compose para inspecciones residenciales y de espacios de eventos, comparación Antes/Después y actas PDF con firmas en pantalla.

## Abrir y ejecutar

1. Abrir esta carpeta **Homecheck_APR** en Android Studio.
2. Usar JDK 21, Android SDK 37 y Android 7/API 24 o superior. El wrapper incluido usa Gradle 9.6.0.
3. Sincronizar Gradle y ejecutar el módulo `app`.
4. Si falta la configuración cliente, prepararla siguiendo el apartado Firebase.

Con `ANDROID_HOME` apuntando al SDK, desde PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Las cachés y los resultados intermedios se regeneran. Esta carpeta conserva únicamente el APK verificado dentro de build.

## Firebase del equipo

El backend es Firebase Authentication y Cloud Firestore. No hay servidor Node, Java o API REST propio que deba iniciarse por separado.

1. Usar la app Android **com.casacheck.app** registrada en el proyecto del equipo (`kotlin-firebase-1ae97`).
2. Descargar su `google-services.json` desde la configuración de Firebase.
3. Ejecutar:

```powershell
.\scripts\configure-firebase.ps1 -GoogleServicesPath 'C:\ruta\google-services.json'
```

Esto crea `app/src/main/assets/firebase-config.json`, excluido del repositorio Git. Cada integrante debe prepararlo en su computadora. No colocar cuentas de servicio, claves administrativas ni contraseñas en el proyecto.

Correo/contraseña está habilitado y las reglas privadas de `firebase/firestore.rules` ya se publicaron en el proyecto del equipo. Para otro proyecto, habilitar Authentication/Firestore y publicar esas reglas; nunca usar reglas abiertas. Ver [backend y estructura](docs/ARQUITECTURA.md).

### Alcance sin facturación

El proyecto se mantiene en Spark. Authentication gestiona las cuentas y Firestore guarda los datos dentro de las cuotas disponibles. No se utiliza Firebase Storage.

**Cuenta → Respaldar datos en Firestore** sube propiedades, inspecciones, ambientes, checklists, inventario, cantidades y observaciones. Las fotos, trazos de firma y PDF permanecen en el teléfono. El respaldo es manual.

La restauración agrega registros nuevos como borradores sin fotos ni firmas, y conserva los registros locales del mismo ID. Requiere completar las evidencias y firmar nuevamente. El respaldo personal no combina ediciones concurrentes. Las propiedades vinculadas tienen un flujo separado para compartir datos entre sus miembros, explicado en docs/PROPIEDADES.md.

Exportar y conservar los PDF antes de borrar datos o cambiar de teléfono. Esta decisión del equipo sustituye el almacenamiento de evidencias en Storage descrito en las páginas 9 y 13 de la propuesta original.

## Organización

- `app/src/main/java/com/casacheck/app/domain`: modelos, plantillas y validaciones.
- `app/src/main/java/com/casacheck/app/data`: persistencia local, Firebase, fotografías y PDF.
- `app/src/main/java/com/casacheck/app/ui`: navegación, pantallas, componentes y tema.
- `app/src/main/java/com/casacheck/app/core`: utilidades compartidas de fechas y firmas.
- `app/src/main/java/com/casacheck/app/platform`: apertura y envío de PDF mediante Android.
- `firebase`: reglas, configuración de emuladores y Hosting de recuperación.
- `scripts`: configuración Firebase y comprobación de permisos.
- `docs`: arquitectura, colaboración, estado de entrega y evidencia de pruebas.

## Pruebas y colaboración

Consultar [guía del equipo](docs/CONTRIBUIR.md) y [resultados de verificación](docs/verification/README.md). Se conservan las pruebas unitarias y de dispositivo; los resultados de Firebase usan datos ficticios.

[Estado y límites conocidos](docs/ESTADO.md). Cámara y navegación se probaron en emulador móvil y con ancho de tableta; no se garantiza compatibilidad con todos los dispositivos físicos ni identidad visual píxel por píxel.

## Compartir la carpeta con el equipo

Compartir esta carpeta directamente y abrir su raíz en Android Studio. La copia de trabajo conserva el archivo cliente Firebase; al clonar desde Git debe generarse con el script de configuración. No contiene credenciales administrativas.

El APK verificado se conserva en `app/build/outputs/apk/debug/app-debug.apk`. Es el único artefacto de compilación que se mantiene en esta entrega. Las cachés y los demás resultados de compilación se regeneran al ejecutar Gradle.

## Registro y contraseña

El acceso requiere cuenta Firebase. El registro muestra errores concretos y fortaleza de contraseña Débil/Media/Fuerte. Las cuentas nuevas requieren mínimo ocho caracteres; las existentes mantienen su contraseña. Ver [registro y recuperación de perfil](docs/REGISTRO.md).

## Recuperar contraseña

La recuperación tiene pantallas independientes para solicitud, revisión del correo y contraseña nueva. La implementación recibe el enlace directamente en la app, sin pegarlo. La asociación del dominio está publicada y Google Digital Asset Links la reconoce; consultar la verificación Android en docs/RECUPERACION.md. Ver [flujo y pruebas de recuperación](docs/RECUPERACION.md).

## Propietarios e inquilinos

Inicio → Propiedades incorpora propiedades privadas, anuncios públicos de alquiler, solicitudes, invitaciones y datos de inspecciones compartidos. Ver [alcance, activación y pruebas](docs/PROPIEDADES.md). Las reglas nuevas se publicaron en kotlin-firebase-1ae97 el 28/10/2026; fotos, firmas y PDF siguen locales.

## Lógica de los tres perfiles

Propietario administra sus inmuebles y alquileres; Inquilino solicita y documenta su alquiler; Encargado trabaja solo en espacios que el propietario le asigna. Los paneles y permisos son distintos. Ver [roles y demostración con tres cuentas](docs/ROLES.md).
