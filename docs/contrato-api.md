# Contrato de la API de ResiAPI

Base: `/api/v1`. Formato JSON (UTF-8). Autenticación: `Authorization: Bearer <JWT>` obtenido en `POST /auth/login`.
El rol del usuario va firmado dentro del token y cada ruta lo exige con `@PreAuthorize`.

## Códigos de estado comunes

| Código | Cuándo | Cuerpo |
|---|---|---|
| 200 / 201 | Operación correcta | recurso o lista |
| 204 | Operación correcta sin cuerpo (`olvide-clave`, `cambiar-clave`, marcar notificaciones, dispositivos, perfil/correo/codigo, justificar falta) | vacío |
| 400 | Datos inválidos o regla de negocio incumplida (fecha pasada, motivo vacío, curso/foto faltante…) | `{ "status": 400, "error": "Bad Request", "message": "...", "validationErrors": { "campo": "detalle" } }` |
| 401 | Sin token, token vencido, usuario inactivo o credenciales erróneas | mismo formato (`validationErrors` null) |
| 403 | Token válido pero el rol no tiene el privilegio. La sesión no se cierra | mismo formato |
| 404 | El recurso no existe o no es del usuario | mismo formato |
| 405 | Método no permitido | mismo formato |
| 409 | Conflicto (duplicado, ya registrado, estado que no lo permite) | mismo formato |
| 413 | Archivo mayor de 5 MB (`POST /evidencias`) | mismo formato |
| 429 | Demasiados intentos fallidos de ingreso; cabecera `Retry-After` en segundos | mismo formato |
| 500 | Error interno | mismo formato |

Rutas públicas (sin token): `POST /auth/login`, `POST /auth/olvide-clave`, `GET|POST /api/v1/publico/apoderado/{token}` (JSON) y, fuera de `/api/v1`, `GET|POST /publico/aprobacion/{token}` (página HTML) y `GET /panel` (redirige al panel web estático `/panel/index.html`). No hay `/actuator/health`.

Roles: `RESIDENTE`, `PRECEPTOR` (pastor o monitor), `GARITA`, `APODERADO`, `ADMINISTRADOR`.

## Autenticación y perfil

| Método y ruta | Roles | Descripción |
|---|---|---|
| `POST /auth/login` | público | Usuario o código + clave; devuelve token y datos de sesión |
| `POST /auth/olvide-clave` | público | Solicita el restablecimiento de clave (204) |
| `POST /auth/cambiar-clave` | autenticado | Cambia la clave propia |
| `GET /auth/me` | autenticado | Sesión actual |
| `GET /perfil`, `PATCH /perfil`, `PUT /perfil/foto` | autenticado | Perfil propio (el residente solo edita correo, celular y foto) |
| `POST /perfil/correo/codigo`, `POST /perfil/correo/verificar` | autenticado | Verificación de correo |

## Residente

| Método y ruta | Roles | Descripción |
|---|---|---|
| `GET /residentes/yo`, `PATCH /residentes/yo` | RESIDENTE | Ficha propia |
| `GET /residentes/yo/qr` | RESIDENTE | QR personal único |
| `GET /residentes/yo/ranking?desde&hasta` | RESIDENTE | Posición en cultos y limpieza (semestre o rango) |
| `GET /residentes/yo/asistencias`, `/dias-con-culto`, `/limpieza`, `/limpieza/resumen`, `/movimientos`, `/habilitacion`, `/resumen-mensual` | RESIDENTE | Historial propio |

## Permisos

| Método y ruta | Roles | Descripción |
|---|---|---|
| `POST /permisos/solicitudes` | RESIDENTE | Crea un permiso (corto, largo, pernocte); fechas no pueden ser pasadas; sin autorización vigente del tipo pasa por el apoderado |
| `GET /permisos/solicitudes/mias`, `POST …/{id}/cancelar`, `…/ampliaciones`, `…/justificaciones` | RESIDENTE | Seguimiento propio |
| `GET /permisos/solicitudes`, `/aprobados-hoy`, `/de-suspendidos`, `/{id}/resumen-residente` | PRECEPTOR, ADMINISTRADOR | Bandeja del pastor |
| `GET /permisos/solicitudes/{id}` | RESIDENTE, PRECEPTOR, ADMINISTRADOR | Detalle de un permiso (el residente solo ve los suyos) |
| `POST /permisos/solicitudes/{id}/decision`, `/anular` | PRECEPTOR | Aprueba o rechaza (motivo obligatorio al rechazar o anular) |
| `POST /permisos/solicitudes/{id}/enlace-apoderado` | PRECEPTOR, ADMINISTRADOR | Enlace público para el apoderado |
| `GET /permisos/ampliaciones/pendientes`, `POST …/{id}/decision`; `GET /permisos/justificaciones/pendientes`, `POST …/{id}/decision` | PRECEPTOR | Ampliaciones y justificaciones |
| `GET|POST /familia/hijos`, `/familia/solicitudes[/{id}[/decision\|/cancelar]]` | APODERADO | Apoderado ve y decide solicitudes de sus hijos |
| `GET /residentes/{id}/autorizaciones`, `POST …`, `POST /autorizaciones/{id}/revocar` | ADMINISTRADOR | Autorizaciones permanentes por tipo de permiso |

## Garita

| Método y ruta | Roles | Descripción |
|---|---|---|
| `POST /garita/verificaciones` | GARITA, PRECEPTOR, ADMINISTRADOR | Lee QR o código de 9 dígitos y devuelve los permisos aplicables |
| `POST /garita/permisos-del-residente` | GARITA | Permisos vigentes del residente |
| `POST /garita/movimientos` | GARITA | Registra salida o retorno con el permiso elegido |
| `GET /garita/permisos`, `GET /garita/movimientos` | GARITA (movimientos también PRECEPTOR, ADMINISTRADOR) | Historial |
| `POST /garita/movimientos/{id}/anular` | PRECEPTOR, ADMINISTRADOR | Anula con motivo |

## Cultos, limpieza, reservas y calendario

| Método y ruta | Roles | Descripción |
|---|---|---|
| `/cultos/sesiones` (POST, GET, `/hoy`, `/{id}`, `/{id}/asistencias`, `/{id}/presentes`, `/{id}/pendientes`, `/{id}/cerrar`, `/{id}/reabrir`, `/{id}/exportar`), `/cultos/presentes-hoy`, `/cultos/asistencias/{id}/anular` | PRECEPTOR, ADMINISTRADOR | Culto del día y asistencia por QR |
| `DELETE /cultos/sesiones/{id}` | PRECEPTOR | Elimina un culto sin asistencias |
| `/calendario/dias-sin-culto` (GET, POST, DELETE `/{fecha}`) | lectura autenticada; escritura PRECEPTOR, ADMINISTRADOR | Días sin culto |
| `POST /limpieza/habitaciones/{id}/evaluaciones`, `/reemplazar`, `/eliminar`, `POST /limpieza/eliminar-dia`, `POST /limpieza/evaluaciones` | PRECEPTOR | Evalúa, modifica o elimina (con motivo y constancia) |
| `GET /limpieza/habitaciones`, `/habitaciones/{id}/evaluacion`, `/evaluaciones` | PRECEPTOR, ADMINISTRADOR | Consulta |
| `GET /reservas/oferta`, `POST /reservas`, `POST /reservas/{id}/cancelar`, `GET /reservas/mias` | RESIDENTE | Reservas de alimentación (foto y curso obligatorios) |
| `POST /reservas/abrir`, `/cerrar`, `/{id}/decision`, `/{id}/cancelar-pastor` | PRECEPTOR | El pastor abre, decide, cancela y cierra (lo no revisado queda rechazado) |
| `GET /reservas/del-dia`, `/aperturas`, `/cierres`, `/exportar` | PRECEPTOR, ADMINISTRADOR | Consulta y CSV |

## Administración

| Método y ruta | Roles | Descripción |
|---|---|---|
| `GET /residentes?pagina=0&tamanio=20`, `GET /residentes/{id}` | ADMINISTRADOR, PRECEPTOR | Lista paginada (tamaño máximo 100) y detalle |
| `POST /residentes`, `PATCH /residentes/{id}`, `POST /residentes/{id}/liberar-habitacion`, `/asignacion`, `/apoderados`, `/qr/regenerar` | ADMINISTRADOR | Alta y gestión |
| `GET /residentes/{id}/apoderados` | ADMINISTRADOR, PRECEPTOR | Apoderados del residente |
| `GET /habitaciones`, `/habitaciones/{id}/ocupantes`, `/habitaciones/ocupacion` | ADMINISTRADOR, PRECEPTOR | Cuartos y ocupantes |
| `POST /habitaciones`, `PUT /habitaciones/{id}` | ADMINISTRADOR | Alta y edición de cuartos |
| `GET /semestres`, `/semestres/actual`; `POST /semestres`, `/semestres/terminar` | lectura autenticada; escritura ADMINISTRADOR | Semestres (crear uno vacía las asignaciones) |
| `/usuarios` (GET paginado con `pagina` y `tamanio`, POST, PATCH `/{id}`) | ADMINISTRADOR | Usuarios |
| `GET /configuracion`, `PUT /configuracion/{clave}`, `GET /auditoria` | ADMINISTRADOR | Parámetros y bitácora |
| `GET /reportes/mensual`, `/reportes/mensual.xlsx` | PRECEPTOR, ADMINISTRADOR | Reporte mensual |
| `GET /habilitacion/faltas-por-justificar` | PRECEPTOR, ADMINISTRADOR | Faltas pendientes de justificar |
| `POST /habilitacion/faltas/justificar` | PRECEPTOR | Justifica una falta (204) |

## Otros

| Método y ruta | Roles | Descripción |
|---|---|---|
| `POST /evidencias` (multipart), `GET /evidencias/{id}` | todos los roles autenticados | Fotos (perfil, reservas, justificaciones) |
| `/notificaciones` (GET, `/no-leidas`, POST `/{id}/leida`, `/leidas`, `/dispositivos`, DELETE `/dispositivos`) | autenticado | Bandeja y registro de dispositivo |
