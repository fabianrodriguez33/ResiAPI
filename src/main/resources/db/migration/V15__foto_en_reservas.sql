-- Foto o archivo que respalda la reserva (opcional): apunta a una evidencia ya subida por quien la crea.
ALTER TABLE reserva_alimentacion ADD COLUMN evidencia_id BIGINT REFERENCES evidencia (id);
