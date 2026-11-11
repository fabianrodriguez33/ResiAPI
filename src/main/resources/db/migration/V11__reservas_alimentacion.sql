-- Reservas de alimentación: cena en táper, cena de segundo turno, almuerzo y desayuno frío.
-- fecha = día en que se sirve la comida (el desayuno frío se pide el día anterior). Nunca se borra una reserva: se cancela.
CREATE TABLE reserva_alimentacion (
    id                 BIGSERIAL    PRIMARY KEY,
    residente_id       BIGINT       NOT NULL REFERENCES residente (id),
    tipo               VARCHAR(20)  NOT NULL CHECK (tipo IN ('CENA_TAPER', 'CENA_SEGUNDO_TURNO', 'ALMUERZO', 'DESAYUNO_FRIO')),
    fecha              DATE         NOT NULL,
    motivo             VARCHAR(300) NOT NULL,
    curso              VARCHAR(100),
    observacion        VARCHAR(150),
    estado             VARCHAR(20)  NOT NULL CHECK (estado IN ('RESERVADA', 'CANCELADA_RESIDENTE', 'CANCELADA_PASTOR', 'CANCELADA_CIERRE')),
    creada_por         BIGINT       NOT NULL REFERENCES usuario (id),
    creada_en          TIMESTAMPTZ  NOT NULL,
    cancelada_por      BIGINT       REFERENCES usuario (id),
    cancelada_en       TIMESTAMPTZ,
    motivo_cancelacion VARCHAR(300)
);

-- Una reserva activa por residente, día y tipo; las dos cenas comparten la misma restricción (una sola cena por día).
-- Las reservas canceladas no cuentan, así que no bloquean una reserva nueva.
CREATE UNIQUE INDEX ux_reserva_activa
    ON reserva_alimentacion (residente_id, fecha, (CASE WHEN tipo IN ('CENA_TAPER', 'CENA_SEGUNDO_TURNO') THEN 'CENA' ELSE tipo END))
    WHERE estado = 'RESERVADA';

CREATE INDEX ix_reserva_fecha_tipo ON reserva_alimentacion (fecha, tipo);
CREATE INDEX ix_reserva_residente_fecha ON reserva_alimentacion (residente_id, fecha);

-- Estado actual de cada día del comedor (una fila por fecha que alguna vez se cerró). No se borra: al reabrir pasa a cerrado = false.
CREATE TABLE dia_comedor (
    fecha           DATE         PRIMARY KEY,
    cerrado         BOOLEAN      NOT NULL,
    motivo          VARCHAR(300),
    actualizado_por BIGINT       NOT NULL REFERENCES usuario (id),
    actualizado_en  TIMESTAMPTZ  NOT NULL
);

-- Historial de cierres y reaperturas: un evento por cada acción, solo se agregan filas (cerrar, reabrir y volver a cerrar
-- dejan tres eventos, sin sobrescribir nada).
CREATE TABLE evento_dia_comedor (
    id         BIGSERIAL    PRIMARY KEY,
    fecha      DATE         NOT NULL,
    accion     VARCHAR(10)  NOT NULL CHECK (accion IN ('CERRADO', 'REABIERTO')),
    motivo     VARCHAR(300),
    usuario_id BIGINT       NOT NULL REFERENCES usuario (id),
    creado_en  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_evento_dia_comedor_fecha ON evento_dia_comedor (fecha, id);
