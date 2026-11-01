-- Cuenta propia para padres/madres/apoderados, privilegio de solicitar permisos y tipo «pernoctar fuera».

ALTER TABLE usuario DROP CONSTRAINT usuario_rol_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check CHECK (rol IN ('RESIDENTE', 'PRECEPTOR', 'GARITA', 'ADMINISTRADOR', 'APODERADO'));

-- Residentes con documento firmado por sus padres pueden pedir ellos mismos permisos corto y largo.
ALTER TABLE residente ADD COLUMN puede_solicitar_solo BOOLEAN NOT NULL DEFAULT FALSE;

-- Un apoderado con cuenta en la app (opcional: los demás siguen usando el enlace de un solo uso).
ALTER TABLE apoderado ADD COLUMN usuario_id BIGINT UNIQUE REFERENCES usuario (id);

-- Tipos: CORTO, LARGO y PERNOCTE (antes ESPECIAL).
ALTER TABLE solicitud_permiso DROP CONSTRAINT solicitud_permiso_tipo_check;
UPDATE solicitud_permiso SET tipo = 'PERNOCTE' WHERE tipo = 'ESPECIAL';
ALTER TABLE solicitud_permiso ADD CONSTRAINT solicitud_permiso_tipo_check CHECK (tipo IN ('CORTO', 'LARGO', 'PERNOCTE'));

ALTER TABLE solicitud_permiso ADD COLUMN origen VARCHAR(10) NOT NULL DEFAULT 'RESIDENTE' CHECK (origen IN ('RESIDENTE', 'APODERADO'));
ALTER TABLE solicitud_permiso ADD COLUMN creado_por BIGINT REFERENCES usuario (id);
ALTER TABLE solicitud_permiso ADD COLUMN responsable_nombre VARCHAR(150);
ALTER TABLE solicitud_permiso ADD COLUMN responsable_celular VARCHAR(30);
ALTER TABLE solicitud_permiso ADD COLUMN acompanante VARCHAR(150);

-- Permiso corto: máximo 2 horas (si pasa, debe pedirse largo).
UPDATE configuracion SET valor = '2', descripcion = 'Duración máxima de un permiso corto en horas; si pasa, debe ser largo'
WHERE clave = 'permiso.corto.max_horas';
