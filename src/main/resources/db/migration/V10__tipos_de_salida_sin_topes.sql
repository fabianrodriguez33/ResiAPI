-- Tipos de salida (2026-11-05): corta hasta 2 h; larga más de 2 h regresando el mismo día; pernoctar fuera si regresa otro día.
-- Ya no hay tope de horas para la larga ni anticipación mínima para ningún tipo.
DELETE FROM configuracion WHERE clave IN ('permiso.largo.max_horas', 'permiso.anticipacion_horas');
