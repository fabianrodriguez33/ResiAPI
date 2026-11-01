-- Datos personales para todos los usuarios: DNI, fecha de nacimiento, correo (verificable) y teléfono.
-- El correo y el teléfono pasan de la ficha del residente a la cuenta, para que el pastor y el resto también los tengan.
ALTER TABLE usuario ADD COLUMN dni VARCHAR(8) CHECK (dni ~ '^[0-9]{8}$');
ALTER TABLE usuario ADD COLUMN fecha_nacimiento DATE;
ALTER TABLE usuario ADD COLUMN email VARCHAR(150);
ALTER TABLE usuario ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN telefono VARCHAR(20);

UPDATE usuario u SET email = r.email, telefono = r.telefono FROM residente r WHERE r.usuario_id = u.id;
ALTER TABLE residente DROP COLUMN email;
ALTER TABLE residente DROP COLUMN telefono;

CREATE UNIQUE INDEX ux_usuario_dni ON usuario (dni) WHERE dni IS NOT NULL;
-- Dos cuentas pueden escribir el mismo correo mientras no esté verificado, pero solo una puede tenerlo verificado.
CREATE UNIQUE INDEX ux_usuario_email_verificado ON usuario (lower(email)) WHERE email_verificado;

-- Código de 6 dígitos para confirmar el correo: solo se guarda su hash, vence y tiene un máximo de intentos.
CREATE TABLE verificacion_correo (
    usuario_id  BIGINT       PRIMARY KEY REFERENCES usuario (id),
    correo      VARCHAR(150) NOT NULL,
    codigo_hash VARCHAR(64)  NOT NULL,
    enviado_en  TIMESTAMPTZ  NOT NULL,
    expira_en   TIMESTAMPTZ  NOT NULL,
    intentos    SMALLINT     NOT NULL DEFAULT 0
);

-- El pastor puede anular un permiso aprobado.
ALTER TABLE solicitud_permiso DROP CONSTRAINT solicitud_permiso_estado_check;
ALTER TABLE solicitud_permiso ADD CONSTRAINT solicitud_permiso_estado_check CHECK (estado IN
    ('BORRADOR', 'PENDIENTE_APODERADO', 'PENDIENTE_PRECEPTOR', 'APROBADO', 'RECHAZADO', 'CANCELADO', 'VENCIDO', 'ANULADO'));

-- Días de lunes a viernes sin cultos (feriados, vacaciones). No cortan la racha de asistencia ni cuentan como faltas.
CREATE TABLE dia_sin_culto (
    fecha     DATE         PRIMARY KEY,
    motivo    VARCHAR(120) NOT NULL,
    creado_por BIGINT      REFERENCES usuario (id),
    creado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);
