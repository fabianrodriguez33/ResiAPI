-- Autorización previa por estudiante y por tipo de permiso (hoja firmada por los padres). Con una autorización vigente
-- para el tipo, el estudiante envía su permiso directo al pastor; sin ella, primero lo acepta su apoderado.
-- Nunca se borra: se revoca con motivo, quién y cuándo.
CREATE TABLE autorizacion_permiso (
    id             BIGSERIAL    PRIMARY KEY,
    residente_id   BIGINT       NOT NULL REFERENCES residente (id),
    tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('CORTO', 'LARGO', 'PERNOCTE')),
    inicio         DATE         NOT NULL,
    fin            DATE,
    referencia     VARCHAR(100) NOT NULL,
    registrada_por BIGINT       NOT NULL REFERENCES usuario (id),
    registrada_en  TIMESTAMPTZ  NOT NULL,
    revocada       BOOLEAN      NOT NULL DEFAULT FALSE,
    revocada_por   BIGINT       REFERENCES usuario (id),
    revocada_en    TIMESTAMPTZ,
    motivo_revocacion VARCHAR(300)
);
CREATE INDEX ix_autorizacion_residente ON autorizacion_permiso (residente_id, tipo);
