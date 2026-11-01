-- La evaluación por habitación (basura y olores comunes) conserva la habitación evaluada.
ALTER TABLE evaluacion_limpieza ADD COLUMN habitacion_id BIGINT REFERENCES habitacion (id);
CREATE INDEX ix_evaluacion_habitacion_fecha ON evaluacion_limpieza (habitacion_id, fecha) WHERE habitacion_id IS NOT NULL;
