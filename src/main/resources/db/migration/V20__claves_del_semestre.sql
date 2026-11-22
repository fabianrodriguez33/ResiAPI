-- Las fechas del semestre activo las usan los rankings; si las claves no existían, se crean.
INSERT INTO configuracion (clave, valor, descripcion) VALUES
  ('ciclo.inicio', '2026-08-01', 'Inicio del semestre activo (la fija el administrador al crear un semestre)'),
  ('ciclo.fin', '2026-12-31', 'Fin del semestre activo')
ON CONFLICT (clave) DO NOTHING;
