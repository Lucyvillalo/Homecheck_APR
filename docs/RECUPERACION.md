# Recuperación de contraseña

## Recorrido

Desde el login, «¿Olvidaste tu contraseña?» abre una pantalla independiente. Solo se necesita el correo de la cuenta; no se solicita la contraseña anterior. Se explica dónde buscar el mensaje, cómo corregir el correo y cómo reenviar después de sesenta segundos. La confirmación no revela si existe una cuenta ni garantiza entrega al buzón.

El enlace HTTPS del correo se recibe directamente en HomeCheck mediante App Links. La app comprueba el código con Firebase antes de mostrar la nueva contraseña. No existe un campo para copiar y pegar enlaces. Se muestran fortaleza, mínimo de ocho caracteres, confirmación y errores para enlaces inválidos, caducados o utilizados.

Después del cambio se vuelve al login con el correo conservado y sin conservar la contraseña. La anterior deja de funcionar. La rotación borra una contraseña todavía no guardada y vuelve a verificar el enlace. Un nuevo enlace recibido con la app abierta sustituye al anterior.

## Configuración publicada

Proyecto kotlin-firebase-1ae97; paquete com.casacheck.app.

- SHA-1 y SHA-256 del APK debug local registradas en Firebase.
- /.well-known/assetlinks.json publicado en Hosting, HTTP 200 y Content-Type application/json.
- Google Digital Asset Links confirmó linked=true para el dominio kotlin-firebase-1ae97.firebaseapp.com y esa firma.
- Manifest con autoVerify para /__/auth/links y /__/auth/action; MainActivity singleTask recibe también onNewIntent.
- Página de continuación con el logo del proyecto publicada en la raíz, HTTP 200, para que el retorno web no termine en 404. El formulario web de Firebase sigue gestionando sus rutas reservadas.

Comprobado en Android 12/API 31: el dominio aparece verified y el manejo de enlaces permitido. Al lanzar tanto /__/auth/action como /__/auth/links mediante ACTION_VIEW, sin paquete ni componente explícito, Android abrió com.casacheck.app/.MainActivity. El segundo enlace llegó a la misma actividad ya abierta. Ver verification/firebase-deployment-results.txt y link-error-api31.png.

Cada certificado de firma necesita su propia entrada SHA-256 en assetlinks y sus huellas registradas en la app Android Firebase. La asociación actual corresponde al APK debug de esta computadora; una APK compilada y firmada por otro integrante requiere registrar su certificado público. No compartir claves privadas.

## Seguridad

El código y las contraseñas permanecen en memoria y no se imprimen en logs ni se guardan con rememberSaveable. Se valida HTTPS, dominio del proyecto, ruta y tipo resetPassword; los enlaces envolventes también deben pertenecer al proyecto. No se siguen redirecciones arbitrarias. Firebase valida y consume el código.

No se cambian contraseñas ni se solicitan correos de personas reales durante las pruebas automáticas. La entrega al buzón y la preferencia del usuario de abrir enlaces en un navegador necesitan comprobación en su teléfono.

## Pruebas reproducibles

Requiere Firebase CLI y un emulador Android con acceso a 10.0.2.2. Desde la raíz:

```powershell
firebase emulators:start --only auth --project demo-homecheck-reset --config firebase/qa/auth-emulator.json
```

En otra terminal:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e recoveryEmulator true -e class com.casacheck.app.RecoveryIntentTest com.casacheck.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e recoveryEmulator true -e class com.casacheck.app.PasswordRecoveryEmulatorTest,com.casacheck.app.AccountAccessTest com.casacheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

Ejecutar ambas órdenes de instrumentación por separado: RecoveryIntentTest inicializa Firebase por defecto con el proyecto demo. El cliente normal de producción no se conecta a emuladores. La excepción HTTP de pruebas se limita al puente local en src/debug.

Resultado local observado: 2 pruebas de intent/ciclo de vida y 6 de recuperación/cuenta aprobadas en API 24. El código válido generado por el Auth oficial local cambió realmente la contraseña y rechazó la anterior. Ver verification/recovery-intent-results.txt. Las pruebas del emulador no demuestran entrega de correo real ni selección de enlaces por el sistema operativo.

Verificación adicional en producción: AccountRegistrationLiveTest y PasswordRecoveryLiveValidationTest aprobaron (2 casos, 49.168 s, API 31). La cuenta ficticia pudo registrarse e iniciar sesión, el correo duplicado produjo un error concreto y el endpoint real rechazó explícitamente el código inválido de QA. No se enviaron correos de recuperación a personas reales.
