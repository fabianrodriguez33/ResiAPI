-- Semestre de la residencia (p. ej. 2026-2). Solo uno está activo; al crear uno nuevo el anterior se cierra y las habitaciones quedan vacías.
CREATE TABLE semestre (
    id         BIGSERIAL    PRIMARY KEY,
    nombre     VARCHAR(20)  NOT NULL UNIQUE,
    inicio     DATE         NOT NULL,
    fin        DATE         NOT NULL,
    activo     BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_por BIGINT       REFERENCES usuario (id),
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cerrado_en TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_semestre_activo ON semestre (activo) WHERE activo;
INSERT INTO semestre (nombre, inicio, fin, activo) VALUES ('2026-2', DATE '2026-08-01', DATE '2026-12-31', TRUE);
