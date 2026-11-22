# Despliegue en Azure (máquina virtual con Docker)

Pensado para Azure for Students. Una sola máquina virtual Linux ejecuta la base de datos, la API y un proxy con HTTPS automático. Es lo más barato y reutiliza el mismo `docker compose` del desarrollo.

> Los precios son orientativos; verifícalos en el portal antes de crear nada. Detén la máquina cuando no la uses (en el portal: **Detener** libera el cómputo y deja de cobrar el cómputo; el disco y la IP sí siguen cobrando poco).

## 1. Crear la máquina virtual (portal de Azure)

1. **Crear un recurso → Máquina virtual.**
2. Imagen: **Ubuntu Server 22.04 LTS**. Tamaño: **Standard_B2s** (2 vCPU, 4 GB). Con 2 GB la API y la base apenas caben.
3. Autenticación: clave SSH (descarga el `.pem` y guárdalo fuera de cualquier repositorio).
4. Red: abre los puertos **80** y **443** a todo internet y el **22** solo a tu IP.
5. En la IP pública, configura una **etiqueta de nombre DNS** (por ejemplo `resiup-equipo`). El nombre público queda `resiup-equipo.<región>.cloudapp.azure.com` y es el que va en `SITE_HOST`.

## 2. Preparar el servidor

```bash
ssh -i resiup.pem azureuser@resiup-equipo.<región>.cloudapp.azure.com
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
sudo usermod -aG docker $USER && exit      # vuelve a entrar para que surta efecto
```

## 3. Obtener el código y configurarlo

```bash
git clone https://github.com/fabianrodriguez33/ResiAPI.git && cd ResiAPI
git checkout v1.0-final        # o la rama develop mientras no exista la etiqueta
cp .env.example .env
nano .env
```

Variables obligatorias en `.env` (genera valores propios; **no** los subas al repositorio):

| Variable | Qué es |
|---|---|
| `SITE_HOST` | nombre público de la máquina (el del paso 1.5) |
| `DB_PASSWORD` | contraseña de la base (larga y aleatoria) |
| `JWT_SECRET` | clave de firma de los tokens: `openssl rand -base64 48` |
| `QR_SECRET` | clave de firma de los QR: `openssl rand -base64 48` |
| `ADMIN_USUARIO`, `ADMIN_CLAVE` | primer administrador (se crea solo si no existe ninguno; clave de 8 o más caracteres) |

Opcionales: correo (`SPRING_MAIL_*`), SMS (`TWILIO_*`) y push (`FCM_CREDENCIALES`). Sin ellos el servicio funciona y solo registra los avisos en el log.

## 4. Arrancar

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f api      # espera "Started ResiApiApplication"
```

Flyway crea el esquema al arrancar. Caddy obtiene solo el certificado HTTPS (puede tardar un minuto la primera vez).

## 5. Comprobar

```bash
curl -i https://$SITE_HOST/api/v1/panel              # debe responder 401 (ruta protegida sin token)
curl -s -X POST https://$SITE_HOST/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"<ADMIN_USUARIO>","password":"<ADMIN_CLAVE>"}'   # debe devolver un token
```

## 6. Apuntar la app a este servidor

En `local.properties` de ResiUP (no versionado):

```properties
resi.apiUrlRelease=https://<SITE_HOST>/
```

Después se regeneran los builds firmados (`./gradlew :androidApp:bundleRelease :androidApp:assembleRelease`) y se instala el APK.

## Datos de demostración

La base nace vacía, solo con el administrador inicial. Crea las cuentas de prueba desde la propia app (rol administrador) o cargando un script SQL de demostración **que no contenga datos reales de residentes**. No copies al servidor público las contraseñas ni los datos de las personas reales.

## Operación

- Actualizar: `git pull && docker compose -f docker-compose.prod.yml up -d --build`.
- Respaldo de la base: `docker compose -f docker-compose.prod.yml exec db pg_dump -U resiup resiup > respaldo.sql`.
- Las fotos (evidencias) están en el volumen `resiup-evidencias`.
- Si cambias `JWT_SECRET`, todas las sesiones abiertas dejan de valer.
