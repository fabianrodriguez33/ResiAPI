-- El pastor o monitor también reserva su comida: la reserva queda a nombre de su usuario, sin residente.
ALTER TABLE reserva_alimentacion ALTER COLUMN residente_id DROP NOT NULL;
ALTER TABLE reserva_alimentacion ADD COLUMN usuario_id BIGINT REFERENCES usuario (id);
ALTER TABLE reserva_alimentacion ADD CONSTRAINT ck_reserva_titular CHECK (residente_id IS NOT NULL OR usuario_id IS NOT NULL);

-- Una reserva activa por persona, día y tipo (las dos cenas comparten la restricción), igual que para los residentes.
CREATE UNIQUE INDEX ux_reserva_activa_personal
    ON reserva_alimentacion (usuario_id, fecha, (CASE WHEN tipo IN ('CENA_TAPER', 'CENA_SEGUNDO_TURNO') THEN 'CENA' ELSE tipo END))
    WHERE residente_id IS NULL AND estado IN ('PENDIENTE', 'RESERVADA');
