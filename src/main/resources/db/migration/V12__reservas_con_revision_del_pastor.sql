-- Las reservas pasan por el pastor (o monitor): nacen PENDIENTE y él las aprueba (RESERVADA) o las rechaza (RECHAZADA).
-- Las reservas ya existentes quedan como están: RESERVADA equivale a "aprobada".
ALTER TABLE reserva_alimentacion DROP CONSTRAINT reserva_alimentacion_estado_check;
ALTER TABLE reserva_alimentacion ADD CONSTRAINT reserva_alimentacion_estado_check
    CHECK (estado IN ('PENDIENTE', 'RESERVADA', 'RECHAZADA', 'CANCELADA_RESIDENTE', 'CANCELADA_PASTOR', 'CANCELADA_CIERRE'));

ALTER TABLE reserva_alimentacion
    ADD COLUMN decidida_por        BIGINT       REFERENCES usuario (id),
    ADD COLUMN decidida_en         TIMESTAMPTZ,
    ADD COLUMN comentario_decision VARCHAR(300);

-- Una pendiente también ocupa el cupo del residente: no puede pedir dos veces lo mismo mientras espera.
DROP INDEX ux_reserva_activa;
CREATE UNIQUE INDEX ux_reserva_activa
    ON reserva_alimentacion (residente_id, fecha, (CASE WHEN tipo IN ('CENA_TAPER', 'CENA_SEGUNDO_TURNO') THEN 'CENA' ELSE tipo END))
    WHERE estado IN ('PENDIENTE', 'RESERVADA');
