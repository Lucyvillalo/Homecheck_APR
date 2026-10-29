# Registro y acceso

## Contraseñas

Las cuentas nuevas requieren al menos ocho caracteres. El indicador orientativo muestra Débil, Media o Fuerte según longitud, variedad y patrones comunes. No es una estimación de entropía ni una garantía de seguridad. Se recomiendan doce o más caracteres y variedad. La política que Firebase tenga configurada también puede rechazar una contraseña y se explica el rechazo.

Las cuentas existentes pueden iniciar sesión con su contraseña anterior: no se exige cambiarla a ocho caracteres para acceder. Mostrar/ocultar permite revisar contraseña y confirmación, sin guardarlas en disco ni escribirlas en logs. Las discrepancias, formato de correo y requisitos pendientes aparecen en el formulario antes de enviar.

## Mensajes

Se distinguen correo ya registrado, credenciales incorrectas, contraseña rechazada, cuenta deshabilitada, exceso de intentos, fallo de conexión, espera agotada, configuración inválida y permisos de Firestore. El detalle opcional muestra solo un código de referencia, sin contraseñas, tokens ni respuesta cruda del servidor.

Para login, Firebase puede devolver el mismo error para correo inexistente y contraseña incorrecta. La app muestra «El correo o la contraseña no son correctos» y no afirma distinguir algo que el servidor no informa. Los mensajes de recuperación tampoco revelan si un correo existe.

## Registro incompleto

Crear la identidad Auth y guardar el perfil Firestore son dos operaciones distintas. Si la primera termina y la segunda falla, se cierra la sesión provisional y se explica que la cuenta ya fue creada. Completar registro inicia sesión con la misma contraseña y reintenta el perfil; no crea otra identidad. Si un perfil ya existe tras un timeout, se conserva su rol y fecha de registro.

Un inicio de sesión que encuentre la identidad sin perfil solicita completar el registro. Nunca se pide al usuario inventar otra contraseña para resolver un error de permisos o conexión.

## Verificación

registration-live-tests.txt contiene tres pruebas reales: registro/login/respaldo y aislamiento, recuperación de perfil rechazado por las reglas y creación/login/correo duplicado desde la pantalla. Las cuentas son ficticias y se eliminan de Authentication al terminar; quedan documentos QA por la política de no borrado. No se enviaron correos ni se cambiaron credenciales de personas reales.

PasswordPolicyTest verifica mínimos, patrones comunes, niveles y errores accionables. AccountAccessTest cubre formulario sin acceso invitado, fortaleza visible y confirmación diferente.

La captura original del usuario contenía un mensaje genérico. No es posible reconstruir su código Firebase exacto a partir de esa imagen; esta versión hace visible el diagnóstico del siguiente intento.
