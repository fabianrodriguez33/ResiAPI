-- Las reservas de una comida ya no abren solas: el pastor o monitor las abre (almuerzo, cena o desayuno) para una fecha.
-- Hasta que no las abre, el residente no puede reservar esa comida. Un cierre posterior sigue funcionando igual.
CREATE TABLE apertura_reservas (
    id          BIGSERIAL    PRIMARY KEY,
    fecha       DATE         NOT NULL,
    grupo       VARCHAR(10)  NOT NULL CHECK (grupo IN ('ALMUERZO', 'CENA', 'DESAYUNO')),
    abierto_por BIGINT       NOT NULL REFERENCES usuario (id),
    abierto_en  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_apertura_reservas UNIQUE (fecha, grupo)
);
