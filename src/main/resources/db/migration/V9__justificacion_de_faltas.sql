-- Justificación de una falta a cultos: la registra el pastor con un motivo obligatorio y la falta justificada no cuenta
-- para la inhabilitación semanal. Una falta es un día de lunes a viernes con culto realizado en el que el residente
-- no asistió a ninguno de los cultos; por eso se justifica por (residente, fecha).
CREATE TABLE justificacion_falta (
    id             BIGSERIAL    PRIMARY KEY,
    residente_id   BIGINT       NOT NULL REFERENCES residente (id),
    fecha          DATE         NOT NULL,
    motivo         VARCHAR(300) NOT NULL,
    registrada_por BIGINT       NOT NULL REFERENCES usuario (id),
    registrada_en  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_justificacion_falta UNIQUE (residente_id, fecha)
);
