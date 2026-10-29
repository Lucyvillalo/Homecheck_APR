# Arquitectura y backend

## Responsabilidades

`MainActivity` inicia `ui/HomeCheckApp.kt`, que coordina sesión y navegación. Las pantallas están agrupadas por cuenta, inspección, reportes y propiedades. El tema y los iconos son componentes compartidos.

`domain/model/InspectionModels.kt` define inspecciones, ambientes, elementos y fotografías. `domain/InspectionTemplates.kt` crea las plantillas y `domain/InspectionRules.kt` valida requisitos y construye la fase Después. Esta capa no depende de Firebase ni de Compose.

`data/local/CasaStore.kt` serializa las inspecciones en JSON y las conserva por cuenta. Ante datos ilegibles informa un error y conserva el original. La coordinación de escrituras utiliza Mutex en HomeCheckApp.

`data/remote/FirebaseServices.kt` inicializa el cliente y registra cuentas/perfiles. `CloudInspectionRepository.kt` implementa el respaldo/restauración manual en Firestore. `DataOnlyBackup.kt` elimina del snapshot las rutas de archivos, fotografías adicionales y firmas; el registro restaurado siempre es un borrador. `FirebaseTasks.kt` limita la espera de operaciones remotas.

`data/evidence/EvidenceFiles.kt` guarda archivos de evidencia, corrige EXIF y estampa fecha/hora. `data/report/ActaPdf.kt` compone el acta. `platform/PdfIntents.kt` delega apertura/compartición al selector Android. Los PDF se generan fuera del hilo principal.

`core` contiene utilidades compartidas de formato y codificación de trazos. Las coordenadas de firma usan Offset de Compose; no se presentan como firma certificada.

## Firebase y modelo de datos

Firebase Authentication maneja las contraseñas y entrega el UID/token. La aplicación no almacena contraseñas dentro de los registros.

Cloud Firestore organiza el acceso bajo `usuarios/{uid}`. El perfil contiene nombre, correo, rol y consentimiento. Sus subcolecciones son `propiedades`, `inspecciones`, `espacios`, `checklists`, `inventario` y `elementos`.

Cada documento de inspección contiene referencias, fechas, modo DATA_ONLY y un snapshot JSON. Los registros estructurados se escriben en lotes antes de publicar el snapshot. Los lotes de una inspección grande no constituyen una transacción global: ante un error puede quedar estructura parcial, y repetir el respaldo completa los documentos por ID.

Para el respaldo personal heredado, `firebase/firestore.rules` limita las operaciones al propio UID, valida el tamaño del snapshot y evita cambiar el rol del perfil por actualización. Los roles no conceden acceso a registros ajenos. Las rutas no autorizadas y el borrado de respaldos personales están bloqueados. El esquema conserva la ruta evidencias por compatibilidad con las reglas publicadas, pero esta versión no escribe documentos multimedia allí.

No hay Firebase Storage ni despliegue de funciones de servidor. Las evidencias y actas permanecen en el teléfono; se aplica el plan Spark sin activar facturación. No existe sincronización automática ni resolución de conflictos entre dispositivos.

## Configuración y entrega

`firebase-config.example.json` documenta los tres valores públicos de cliente Android necesarios. El archivo real se genera con `scripts/configure-firebase.ps1` y está excluido de Git. La seguridad depende de Authentication y las reglas, no de ocultar una API key de cliente.

La carpeta `.git` es el historial local y se conserva en el proyecto de trabajo. Las carpetas `.gradle`, `.kotlin` y build son regenerables. La entrega directa conserva únicamente el APK verificado dentro de build; Gradle regenera el resto. La configuración de emuladores está en firebase/firebase.emulators.json.

## Propiedades y anuncios: ampliación de alcance

`data/remote/PropertyRepository.kt` implementa las operaciones Firebase y la validación de entrada; `ui/property/PropertiesScreen.kt` presenta las acciones por rol y los estados de carga, vacío, error y éxito. Los permisos efectivos se aplican en Firestore, no en la selección visual del portal.

- `properties/{id}`: inmueble privado, propietario inmutable, membresía y un único inquilino activo.
- `rentalListings/{id}`: proyección pública con lista cerrada de campos; excluye dirección privada y evidencias. La retirada elimina el anuncio, no la propiedad.
- `rentalApplications/{propertyId_uid}`: solicitud privada visible solo al solicitante y al propietario. El solicitante puede comprobar la ausencia de su propia solicitud antes de crearla mediante una transacción.
- `propertyInvites/{sha256}`: invitación aleatoria, dirigida opcionalmente a un UID, de uso único y con caducidad. La aceptación actualiza invitación y propiedad atómicamente con validaciones cruzadas `getAfter`.
- `properties/{id}/inspections/{authorUid_inspectionId}`: snapshot de datos por autor; ambos miembros pueden leerlo, solo su autor puede escribirlo. La importación valida identificadores y elimina multimedia antes de agregar borradores. No reemplaza registros locales existentes ni agrega IDs duplicados.

La propiedad canónica está en `properties`; los respaldos heredados bajo usuarios no prueban titularidad. Se corrigió su campo de atribución a `idCreador`, eliminando la suposición de que el autor fuera propietario.

Las reglas de esta ampliación están verificadas con Auth y Firestore oficiales locales, y se publicaron en kotlin-firebase-1ae97 el 28/10/2026. No hay pagos ni reservas. Ver PROPIEDADES.md y los resultados en verification/.

## Separación de perfiles y delegación

AccountRole centraliza los perfiles de negocio. AccountWorkspace carga el rol guardado para la sesión; HomeScreen y PropertiesScreen muestran acciones distintas. Las inspecciones del encargado requieren una asignación activa y los datos previos se conservan.

staffAssignments/{propertyId_staffUid} concede acceso operativo sin cambiar memberUids ni tenantUid. staffInvites/{sha256} es una invitación de encargo de un uso, separada del alquiler. StaffAssignmentRepository acepta y revoca la delegación; solo el propietario puede administrar los encargos de su inmueble. Las reglas impiden al encargado publicar, aprobar solicitudes, transferir propiedad o promover su rol.

La aceptación de alquiler retira el anuncio en la misma transacción, y las reglas bloquean publicaciones y aprobaciones mientras esté ocupado. Las solicitudes conservan propertyName, con compatibilidad para registros anteriores.
