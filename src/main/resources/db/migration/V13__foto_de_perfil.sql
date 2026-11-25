-- Foto de perfil: apunta a una evidencia (imagen ya validada) subida por la propia persona.
ALTER TABLE usuario ADD COLUMN foto_evidencia_id BIGINT REFERENCES evidencia (id);
