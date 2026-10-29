# Evidencia de verificación

Se conservan resultados finales y ejemplos con datos ficticios. Los diagnósticos antiguos y capturas duplicadas se retiraron al organizar la entrega.

- completion-phone-tests.txt: ocho pruebas móviles aprobadas.
- completion-tablet-tests.txt: dos recorridos aprobados a 720 dp de ancho.
- firebase-live-results.txt: registro, token, login, respaldo/restauración y reglas privadas aprobados contra Firebase real.
- completion-phone y completion-tablet: capturas de los recorridos; las imágenes de cámara son el patrón del sensor emulado.
- completion-phone/qa-residential-timing.txt: medición de PDF con 47 elementos y fotos de prueba.
- qa-comparative.pdf: ejemplo de acta comparativa con fotografías, notas largas y firmas ficticias.

Los reportes de Gradle y lint se regeneran al ejecutar las verificaciones del README. La carpeta entregada conserva únicamente el APK verificado; los resultados intermedios y cachés se retiraron.

Las pruebas de emulador no acreditan cámara física, envío efectivo a destinatarios ni rendimiento universal. Ver docs/ESTADO.md para límites. La separación por paquetes se verificó con compilación, pruebas unitarias, lint y recorridos móviles; los resultados actuales se resumen en docs/ESTADO.md.

## Registro detallado

- registration-live-tests.txt: tres pruebas reales Firebase aprobadas, incluyendo perfil incompleto y registro/login/correo duplicado por pantalla.
- registration-ui-final-tests.txt: indicador, confirmación diferente, acceso obligatorio y repetición del registro/login/correo duplicado con el APK final.
- qa-registration-duplicate.png: captura real de la fortaleza y el mensaje accionable para un correo ficticio repetido.
- Ver docs/REGISTRO.md para política y límites del diagnóstico.

## Recuperación

- password-recovery-tests.txt: seis pruebas aprobadas del flujo y acceso. Cambio completo en Auth Emulator oficial, contraseña anterior rechazada, nueva aceptada, enlace usado rechazado y regreso al login sin clave antigua.
- password-recovery-live-validation.txt: Firebase real respondió correctamente al verificar un código ficticio inválido; operación de solo lectura.
- recovery/: capturas de las cinco pantallas principales con datos ficticios y secretos ocultos.
- 12 pruebas unitarias aprobadas; compilación y lint sin errores.
- No se verificó entrega a un buzón real. Ver docs/RECUPERACION.md para repetir las pruebas.

## Resultado actual de la ampliación

- properties-results.txt: 32 comprobaciones de reglas y recorrido propietario/inquilino con SDK y Compose.
- recovery-intent-results.txt: dos casos de intent/rotación y seis de recuperación/cuenta.
- firebase-deployment-results.txt: despliegue real, coincidencia de reglas, asociación verificada y dos pruebas contra Firebase real.
- link-error-api31.png: captura del error accionable tras apertura implícita del enlace en Android 12.
- apk-debug.sha256: integridad del APK conservado.

Los informes anteriores documentan pruebas específicas previas; sus cantidades no se suman como pruebas nuevas ni sustituyen el resultado actual. Se retiraron los informes de organización obsoletos y el informe duplicado de acceso.

## Separación de roles — versión 1.2

- roles-permissions-results.txt: 99 comprobaciones contra Auth/Firestore Emulator, incluidos permisos rechazados y revocación.
- roles-ui-results.txt: cuatro recorridos completos de los tres perfiles, OK (4 tests).
- roles/: capturas reales de cada panel con datos ficticios, revisadas visualmente.
- roles-property-regression.txt: SDK y pantalla de propiedades, OK (1 test).
- roles-recovery-regression.txt: enlaces, ciclo de Activity y cambio de contraseña, OK (2 tests).
- roles-access-regression.txt: recuperación y validaciones de acceso, OK (6 tests).
- roles-inspection-regression.txt: creación y rechazo de acta incompleta, OK (1 test).
- roles-build-results.txt: compilación, doce pruebas unitarias, lint y hash del APK 1.2.
- firebase-deployment-results.txt: publicación de las reglas de roles y lectura posterior idéntica a la fuente probada.

Las 14 pruebas Android locales se ejecutaron en procesos separados cuando requerían diferentes configuraciones de Firebase. Los recuentos históricos anteriores no se suman a esta ejecución.

- roles-firebase-live-regression.txt: registro/login/correo duplicado y verificación de un código inválido contra Firebase real, OK (2 tests). Junto a las 14 locales, 16 pruebas Android actuales aprobadas.
