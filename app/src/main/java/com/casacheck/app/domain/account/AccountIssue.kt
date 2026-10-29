package com.casacheck.app.domain.account

data class AccountIssue(val code: String, val message: String)

fun accountIssueFor(code: String): AccountIssue = AccountIssue(code, when (code) {
    "ERROR_EMAIL_ALREADY_IN_USE" -> "Este correo ya está registrado. Pulsa «Ya tengo una cuenta» para iniciar sesión. Si no recuerdas la contraseña, usa «¿Olvidaste tu contraseña?»."
    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "Este correo ya pertenece a una cuenta con otro método de acceso. Utiliza el método con el que la creaste."
    "ERROR_INVALID_EMAIL" -> "El correo no tiene un formato válido. Revisa que incluya @ y un dominio, por ejemplo nombre@correo.com."
    "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> "Firebase rechazó la contraseña porque no cumple la política del proyecto. Usa al menos 8 caracteres, preferiblemente 12 o más, combinando mayúsculas, minúsculas, números y símbolos."
    "PASSWORD_TOO_SHORT" -> "Para crear una cuenta, escribe una contraseña de al menos 8 caracteres."
    "RESET_PASSWORD_TOO_SHORT" -> "La nueva contraseña debe tener al menos 8 caracteres."
    "INVALID_RESET_LINK" -> "El enlace no es válido para HomeCheck. Copia el enlace completo de recuperación recibido por correo, sin modificarlo. No uses un enlace de otra aplicación."
    "ERROR_EXPIRED_ACTION_CODE" -> "El enlace de recuperación caducó. Solicita otro correo y utiliza el enlace más reciente."
    "ERROR_INVALID_ACTION_CODE" -> "El enlace no es válido o ya se utilizó. Si ya cambiaste la contraseña en el navegador, vuelve a iniciar sesión. Si no, solicita otro enlace."
    "NAME_REQUIRED" -> "Escribe tu nombre antes de crear la cuenta."
    "POLICY_REQUIRED" -> "Lee y acepta las condiciones de uso y la política de privacidad para registrarte."
    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_USER_NOT_FOUND" -> "El correo o la contraseña no son correctos. Revisa ambos datos. Si ya tienes cuenta y no recuerdas la contraseña, usa «¿Olvidaste tu contraseña?»."
    "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada. Contacta al equipo responsable para revisar el acceso."
    "ERROR_TOO_MANY_REQUESTS" -> "Firebase ha bloqueado temporalmente los intentos por exceso de solicitudes. Espera unos minutos antes de volver a intentarlo."
    "NETWORK_ERROR", "ERROR_NETWORK_REQUEST_FAILED" -> "No se pudo conectar con Firebase. Comprueba que tengas Internet por Wi-Fi o datos móviles y vuelve a intentarlo."
    "TIMEOUT" -> "Firebase no respondió a tiempo. Comprueba tu conexión y vuelve a intentarlo. No cambies la contraseña por este error."
    "ERROR_OPERATION_NOT_ALLOWED" -> "El acceso con correo y contraseña no está habilitado en Firebase. El equipo debe activar ese proveedor en Authentication."
    "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_APP_CREDENTIAL", "ERROR_MISSING_CLIENT_IDENTIFIER" -> "Firebase rechazó la configuración de esta aplicación. El equipo debe revisar el cliente Android, su clave y el paquete com.casacheck.app. No es un error de tu contraseña."
    "FIRESTORE_PERMISSION_DENIED" -> "Firestore rechazó el guardado del perfil por permisos. El equipo debe revisar las reglas privadas publicadas. No es un error de tu contraseña."
    "FIRESTORE_UNAUTHENTICATED", "ERROR_REQUIRES_RECENT_LOGIN" -> "Tu sesión ya no es válida para completar esta operación. Inicia sesión nuevamente y vuelve a intentarlo."
    "FIRESTORE_RESOURCE_EXHAUSTED", "ERROR_QUOTA_EXCEEDED" -> "Se alcanzó un límite de uso del servicio. Espera antes de reintentar; si continúa, el equipo debe revisar las cuotas del proyecto."
    "FIRESTORE_DEADLINE_EXCEEDED", "FIRESTORE_UNAVAILABLE" -> "No está disponible la base de datos de perfiles. Revisa la conexión y vuelve a intentarlo."
    "PROFILE_REQUIRED" -> "Tu cuenta existe, pero falta completar el perfil. Confirma tu nombre y las condiciones y pulsa «Completar registro». Usa la misma contraseña que ya elegiste."
    else -> "No se pudo completar la operación. Vuelve a intentarlo; si continúa, comparte la referencia del error con el equipo."
})
