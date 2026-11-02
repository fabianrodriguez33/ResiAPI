-- Clave provisional para quien olvidó su contraseña: se guarda aparte (con hash y vencimiento) y la contraseña real
-- sigue valiendo hasta que la persona elija una nueva; así nadie puede dejar sin acceso a otro pidiendo un restablecimiento.
ALTER TABLE usuario ADD COLUMN clave_provisional_hash    VARCHAR(100);
ALTER TABLE usuario ADD COLUMN clave_provisional_expira  TIMESTAMPTZ;
ALTER TABLE usuario ADD COLUMN clave_provisional_enviada TIMESTAMPTZ;

-- Permisos pedidos sin conexión: la app guarda la solicitud con una clave propia y la envía al volver la red.
-- Si el envío se repite (por ejemplo, se cortó la respuesta), el servidor devuelve la solicitud ya creada.
ALTER TABLE solicitud_permiso ADD COLUMN clave_cliente VARCHAR(40);
CREATE UNIQUE INDEX ux_solicitud_clave_cliente ON solicitud_permiso (creado_por, clave_cliente) WHERE clave_cliente IS NOT NULL;
