# Trabajo en equipo

## Preparación

Abrir la raíz en Android Studio, usar JDK 21/SDK 37 y sincronizar Gradle. La configuración cliente Firebase del equipo ya está incluida. Mantener applicationId com.casacheck.app para que coincida con la app registrada. Cada integrante debe usar su propia cuenta de prueba; las reglas separan datos por UID.

## Convenciones

- Presentación en ui, reglas/modelos en domain y acceso a datos en data.
- No añadir dependencias de Compose ni Firebase a domain.
- Usar core solo para utilidades compartidas y platform para integración con Android.
- No subir builds, cachés, configuración de máquina, contraseñas ni claves administrativas.
- Conservar los IDs y el formato JSON al cambiar modelos; considerar compatibilidad con datos locales existentes.
- Probar cambios de reglas con acceso propio y acceso rechazado entre usuarios antes de publicarlas.
- Hacer commits por cambio coherente y revisar el diff antes de compartir.

## Verificaciones locales

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.casacheck.app.CaptureAndSignatureTest,com.casacheck.app.InspectionIntegrationTest,com.casacheck.app.InspectionJourneyTest,com.casacheck.app.AccountAccessTest com.casacheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

La suite de dispositivo debe terminar en OK (11 tests). El código de salida de adb no basta para detectar errores JUnit. Usa un emulador dedicado: estas pruebas crean datos ficticios e inspecciones locales.

## Prueba remota optativa

```powershell
adb shell am instrument -w -r -e firebaseLive true -e class com.casacheck.app.FirebaseLiveTest,com.casacheck.app.AccountRegistrationLiveTest com.casacheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

Requiere conexión y Firebase configurado. Crea solo datos ficticios; no envía correos. Elimina la cuenta temporal Auth y no imprime ni conserva su contraseña. Las reglas prohíben borrar documentos, por lo que quedan los registros ficticios QA bajo ese UID. No ejecutar pruebas usando datos de personas reales ni presentar registros QA como inspecciones terminadas.

## Compartir

Compartir la carpeta directamente o usar Git. Al clonar desde Git, firebase-config.json ya contiene la configuración pública del cliente Android del equipo; basta sincronizar y compilar. Los recursos originales, las pruebas y la licencia Inter deben permanecer en el proyecto. El APK verificado está en app/build/outputs/apk/debug/app-debug.apk. Las cachés y compilaciones temporales no forman parte del código fuente.

## Pruebas de los tres perfiles

Con Firebase CLI, Java 21 y Python, desde la raíz:

```powershell
firebase emulators:exec --only auth,firestore --project demo-homecheck-reset --config firebase/firebase.emulators.json "python scripts/test-role-permissions.py"
```

La suite ejecuta 99 comprobaciones, incluida la regresión de propiedades. No usa producción.

Para las pantallas, iniciar esos mismos emuladores con `emulators:start`, instalar ambos APK y ejecutar en un emulador Android dedicado:

```powershell
adb shell am instrument -w -e class com.casacheck.app.RoleWorkflowEmulatorTest -e propertiesEmulator true com.casacheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

Debe terminar en `OK (4 tests)`. Incluye cuenta con perfil persistente, aislamiento entre propietarios, solicitud y aceptación de alquiler, asignación operativa y revocación sin desvincular al inquilino. Usa cuentas ficticias y guarda capturas en el directorio externo de la app, subcarpeta role-qa. Ejecutar separadamente las suites que inicializan otra instancia Firebase por defecto.
