# ResiAPI

Backend de **ResiUP** (Residencia Universitaria Fernando Stahl – UPeU). Spring Boot 4 · Java 21 · PostgreSQL 17 · JWT.

## Requisitos

- **Docker** y Docker Compose (para levantar la base y la API con un solo comando).
- Para desarrollo sin Docker: **JDK 21** (el wrapper `./mvnw` descarga Maven).
- Para las pruebas: la base `resiup_test` en PostgreSQL 17 (ver *Pruebas*).

## Ejecutar

1. Copiar `.env.example` a `.env` (no se versiona) y completar `JWT_SECRET`, `QR_SECRET` (al menos 32 caracteres cada una), `ADMIN_USUARIO` y `ADMIN_CLAVE` (mínimo 8 caracteres).
2. Levantar todo con un solo comando:

```bash
docker compose up -d --build
```
La API queda en `http://localhost:8080` y el panel web en `http://localhost:8080/panel`. Los datos viven en volúmenes de Docker (`resiup-pgdata`, `resiup-evidencias`); la base solo es accesible desde la propia máquina. Para detener: `docker compose down` (agregar `-v` borraría los datos). Ver los registros: `docker compose logs -f api`.

**Desarrollo (API desde el IDE o Maven, solo la base en Docker):**
```bash
docker compose up -d db
set -a; . ./.env; set +a; ./mvnw spring-boot:run
```

En el primer arranque Flyway crea el esquema y, si no existe administrador, se crea el definido en `ADMIN_USUARIO`/`ADMIN_CLAVE`. Probar: `POST http://localhost:8080/api/v1/auth/login`.

Desde el **emulador Android** la API es `http://10.0.2.2:8080`; desde un **teléfono físico** use la IP de la PC en la red local (y permita el puerto 8080 en el firewall).

## Credenciales de prueba (demo)

| Rol | Usuario | Clave |
|---|---|---|
| Administrador | valor de `ADMIN_USUARIO` en `.env` | valor de `ADMIN_CLAVE` en `.env` |

Las credenciales de demo no se guardan en el repositorio: se definen en el `.env` local antes del primer arranque. Para la demostración del curso, el equipo las comparte por separado. _(Pendiente: completar con las credenciales de demo acordadas.)_

## Pruebas

Pruebas unitarias de servicios (sin base de datos):
```bash
./mvnw test -Dtest='RankingServiceTest,SemestreServiceTest,AutorizacionPermisoServiceTest,HorarioReservasTest'
```

Suite completa (incluye pruebas de integración con PostgreSQL real en una base separada):
```bash
docker exec resiup-db psql -U resiup -c "CREATE DATABASE resiup_test"   # una sola vez
set -a; . ./.env; set +a; ./mvnw test
```

Cobertura (JaCoCo) en `target/site/jacoco/jacoco.xml`, la que lee SonarQube.

Documentación: `docs/API.md`, `docs/BASE_DE_DATOS.md`, `docs/REGLAS_DE_NEGOCIO.md` en la carpeta de documentación del proyecto.
