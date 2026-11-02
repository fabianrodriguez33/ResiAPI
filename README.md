# ResiAPI

Backend de **ResiUP** (Residencia Universitaria Fernando Stahl – UPeU). Spring Boot 4 · Java 21 · PostgreSQL 17 · JWT.

## Ejecutar

Copiar `.env.example` a `.env` y completar los valores (`.env` no se versiona). Claves JWT/QR de al menos 32 caracteres.

**Todo en Docker (base de datos y API), un solo comando:**
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

## Pruebas

Usan PostgreSQL real en una base separada:
```bash
docker exec resiup-db psql -U resiup -c "CREATE DATABASE resiup_test"   # una sola vez
set -a; . ./.env; set +a; ./mvnw test
```

Documentación: `docs/API.md`, `docs/BASE_DE_DATOS.md`, `docs/REGLAS_DE_NEGOCIO.md` en la carpeta de documentación del proyecto.
