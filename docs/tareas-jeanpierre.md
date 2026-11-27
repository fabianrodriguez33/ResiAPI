# Tareas de Jean-Pierre: web y backend

## Antes de empezar

1. Clonar o actualizar `ResiAPI` y partir de la rama de la web:

   ```bash
   git fetch origin
   git switch feature/web-rodriguez
   git switch -c feature/web-miranda
   ```

2. Revisar que `git config user.email` sea un correo vinculado a tu cuenta de GitHub (Settings → Emails). Si no, GitHub no cuenta los commits como tuyos.
3. Levantar el servidor (necesita Docker y un `.env` creado a partir de `.env.example`):

   ```bash
   docker compose up -d --build
   ```

4. Para trabajar la web con recarga automática:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

   Se abre en `http://localhost:5173/app/` y usa el servidor de `localhost:8080`.

Las cuentas de prueba se piden por privado. No se suben al repositorio.

Al terminar: `git push -u origin feature/web-miranda` y abrir una solicitud de incorporación **hacia `feature/web-rodriguez`**, no hacia `main` ni `develop`.

## Tarea 1 · Permisos con horarios cruzados

**Regla:** si ya existe un permiso de 17:00 a 18:00, no se puede enviar otro que toque ese rango (17:00 a 18:00, 17:40 a 18:30, 16:30 a 17:10). Uno de 18:00 a 19:00 sí se permite.

**Lo que ya existe** (verificar antes de escribir código):

- `SolicitudPermisoRepository.existeSolapada` cuenta los permisos en `PENDIENTE_APODERADO`, `PENDIENTE_PRECEPTOR` y `APROBADO` que se cruzan.
- `PermisoService.guardar` lo llama y responde 409: «Ya hay un permiso pendiente o aprobado que se superpone con ese horario». Pasa por ahí tanto el residente como el apoderado.

**Lo que falta:**

- [ ] Pruebas de integración de la regla (no hay ninguna): cruce total, cruce parcial por ambos lados, permiso contiguo (se permite) y permiso cancelado o rechazado (no bloquea).
- [ ] Ampliaciones: `solicitarAmpliacion` y `decidirAmpliacion` no consultan el cruce, así que un retorno ampliado podría pisar otro permiso. Rechazarlo, sin contar el permiso que se está ampliando.
- [ ] Dos envíos al mismo tiempo (doble toque o dos dispositivos): la consulta y el guardado no están bloqueados. Escribir la prueba; si se reproduce, bloquear por residente dentro de la transacción.
- [ ] Comprobar a mano en la app y en la web que el mensaje del 409 se ve.

**Terminado cuando:** `./mvnw test` pasa completo, con las pruebas nuevas.

Si alguien ya vio que se aceptaban dos permisos cruzados, anotar los pasos exactos y las horas antes de cambiar nada.

## Tarea 2 · Diseño de la web

**Mismos colores y tipografías** (están en `frontend/src/styles.css`, tomados de la app):

| Uso | Color |
|---|---|
| Azul institucional | `#012E6A` |
| Azul secundario | `#365586` |
| Azul vivo | `#1F63E0` |
| Fondo | `#F1F5FF` |
| Texto | `#111827` |

Títulos en Prompt, texto en Montserrat.

- [ ] Iconos en el menú, los botones principales y los estados. Una sola familia, por ejemplo `lucide-react` (`npm install lucide-react`). Sin emojis.
- [ ] Un icono nunca va solo: lleva texto, o `aria-label` si de verdad no cabe.
- [ ] Inicio de cada rol con jerarquía clara: lo urgente primero (por revisar, por aceptar, habilitación).
- [ ] Que no parezca plantilla: evitar rejillas de tarjetas iguales, degradados y sombras en todo.
- [ ] Estados vacíos con una frase útil y la acción que corresponde.
- [ ] Revisar a 375 px de ancho, en modo claro y oscuro, y con teclado (el foco debe verse).

**Terminado cuando:** `npm run build` pasa y hay capturas de antes y después en la solicitud de incorporación.

**Dónde tocar:** `frontend/src/styles.css`, `frontend/src/ui.jsx` (piezas comunes), `frontend/src/main.jsx` (menú) y `frontend/src/pages/`.

## No tocar

- `main` y la etiqueta `v1.0-final`: son la entrega.
- Nada se fusiona a `main` antes de la sustentación.
