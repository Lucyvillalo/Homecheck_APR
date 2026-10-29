# Roles y flujo de negocio

Cada cuenta tiene un perfil registrado. La selección del portal de acceso no cambia ese perfil: la sesión lo consulta en Firestore. Los permisos se verifican también en el backend.

## Propietario

Administra únicamente sus inmuebles. Puede registrar propiedades, anunciar las que están disponibles, recibir y decidir solicitudes, invitar o desvincular inquilinos y asignar o retirar encargados. Su panel muestra sus propiedades, sus anuncios y sus solicitudes recibidas. No tiene privilegios sobre otros propietarios.

## Inquilino

Busca alquileres, envía solicitudes y acepta la invitación del propietario. Su panel muestra el catálogo, sus solicitudes y las propiedades vinculadas a su cuenta. Puede documentar el estado y consultar los datos compartidos del alquiler. No publica anuncios, no aprueba solicitudes ni administra encargados.

## Encargado

Es un colaborador operativo, no otro propietario. Debe aceptar una invitación de encargo distinta de la invitación de inquilino. Su panel muestra exclusivamente los espacios asignados y las herramientas para inspecciones, inventario e incidencias. No registra inmuebles, no publica ni retira anuncios, no solicita alquileres, no decide solicitudes y no modifica al inquilino.

Un propietario puede tener varios encargados activos. Solo existe un código de encargo pendiente por propiedad: generar otro sustituye el pendiente, sin retirar los encargos ya aceptados. Retirar un encargo revoca el acceso remoto de ese colaborador y anula los códigos operativos pendientes. No modifica la propiedad ni el vínculo del inquilino.

## Demostración con tres cuentas

1. Registrar una cuenta Propietario y una propiedad disponible; publicar su anuncio.
2. Con otra cuenta Inquilino, buscar el anuncio y enviar una solicitud.
3. El propietario aprueba y el inquilino acepta: se vincula el alquiler y el anuncio se retira en la misma transacción. Mientras siga ocupado no se puede volver a publicar ni aprobar otro inquilino.
4. El propietario pulsa Invitar encargado. Una tercera cuenta Encargado introduce ese código y acepta el encargo.
5. El encargado entra a Espacios asignados y crea una inspección del inmueble; no encuentra controles de publicación ni solicitudes.
6. El propietario retira su acceso. El encargado actualiza y deja de ver el espacio; el inquilino conserva su vínculo y datos.

## Datos y compatibilidad

Las propiedades, alquileres y encargos se mantienen separados: un encargo no agrega al colaborador al arreglo de propietarios/inquilinos ni le transfiere titularidad. Las solicitudes conservan el nombre del anuncio para seguir siendo comprensibles después de retirarlo.

Los registros creados con una cuenta Encargado en la versión anterior se conservan como historial de solo lectura; no otorgan nuevos permisos de propietario. Los archivos locales ya descargados no se borran por una revocación. El archivo de actas conserva los PDF propios existentes; la edición operativa y nuevas inspecciones necesitan una asignación activa.

Fotos, trazos de firma y PDF permanecen locales. El intercambio remoto transmite metadatos y borradores. Las partes firman presencialmente en un mismo dispositivo; el encargado puede preparar la inspección, pero no obtiene una firma o aceptación remota del propietario. No hay pagos, reservas ni administración global de usuarios.

## Verificación

La suite scripts/test-role-permissions.py incluye la regresión de propiedades y comprobaciones adversariales de los tres roles contra los emuladores oficiales. Ver verification/roles-permissions-results.txt. RoleWorkflowEmulatorTest verifica los recorridos mediante SDK Firebase y Compose; debe ejecutarse separadamente de pruebas de producción y de recuperación que inicializan otra instancia Firebase por defecto.
