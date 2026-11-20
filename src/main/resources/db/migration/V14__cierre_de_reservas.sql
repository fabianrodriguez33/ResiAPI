-- El pastor cierra las reservas de una comida (almuerzo, cena o desayuno) de una fecha: las pendientes se cancelan
-- por cierre, ya no se aceptan reservas nuevas de esa comida y la lista queda lista para exportar.
CREATE TABLE cierre_reservas (
    id         BIGSERIAL    PRIMARY KEY,
    fecha      DATE         NOT NULL,
    grupo      VARCHAR(10)  NOT NULL CHECK (grupo IN ('ALMUERZO', 'CENA', 'DESAYUNO')),
    cerrado_por BIGINT      NOT NULL REFERENCES usuario (id),
    cerrado_en TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_cierre_reservas UNIQUE (fecha, grupo)
);
