-- Perfil editable del residente: correo, carrera y ciclo (el teléfono y el código universitario ya existen).
ALTER TABLE residente ADD COLUMN email VARCHAR(150);
ALTER TABLE residente ADD COLUMN carrera VARCHAR(100);
ALTER TABLE residente ADD COLUMN ciclo SMALLINT CHECK (ciclo BETWEEN 1 AND 14);

-- El pastor ya no aprueba permisos: solo consulta la lista. Los que esperaban su revisión quedan aprobados si la salida
-- todavía no pasó, o vencidos si ya pasó.
UPDATE solicitud_permiso
SET estado = CASE WHEN salida_solicitada > now() THEN 'APROBADO' ELSE 'VENCIDO' END,
    decidido_en = COALESCE(decidido_en, now()),
    actualizado_en = now()
WHERE estado = 'PENDIENTE_PRECEPTOR';
