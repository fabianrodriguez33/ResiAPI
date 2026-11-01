-- Bandeja de notificaciones de cada usuario (lo que además se envía por push, correo y SMS).
CREATE TABLE notificacion (
    id         BIGSERIAL    PRIMARY KEY,
    usuario_id BIGINT       NOT NULL REFERENCES usuario (id),
    titulo     VARCHAR(120) NOT NULL,
    mensaje    VARCHAR(400) NOT NULL,
    leida      BOOLEAN      NOT NULL DEFAULT FALSE,
    creada_en  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_notificacion_usuario ON notificacion (usuario_id, creada_en DESC);

-- Teléfonos registrados para recibir notificaciones push (token de FCM).
CREATE TABLE dispositivo (
    id         BIGSERIAL    PRIMARY KEY,
    usuario_id BIGINT       NOT NULL REFERENCES usuario (id),
    token      VARCHAR(400) NOT NULL UNIQUE,
    plataforma VARCHAR(10)  NOT NULL CHECK (plataforma IN ('ANDROID', 'IOS')),
    creado_en  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_dispositivo_usuario ON dispositivo (usuario_id);
