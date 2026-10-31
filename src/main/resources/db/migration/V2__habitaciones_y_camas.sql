-- Camas: letra dentro de la habitación (101-A, 101-B...). Única entre asignaciones vigentes.
ALTER TABLE asignacion_habitacion ADD COLUMN cama VARCHAR(2);
CREATE UNIQUE INDEX uq_cama_vigente ON asignacion_habitacion (habitacion_id, cama) WHERE hasta IS NULL AND cama IS NOT NULL;

-- 39 habitaciones de la residencia (capacidad total 106).
INSERT INTO habitacion (numero, capacidad)
SELECT n, c FROM (VALUES
  ('101',2),('102',2),('103',2),('104',2),('105',4),('106',4),
  ('201',2),('202',4),('203',2),('204',4),('205',2),('206',2),('207',2),('208',2),('209',2),
  ('210',2),('211',2),('212',2),('213',4),('214',4),('215',2),('216',2),('217',2),('218',2),
  ('301',4),('302',2),('303',4),('304',2),('305',4),('306',2),('307',4),('308',2),
  ('401',4),('402',2),('403',4),('404',2),('405',4),('406',2),('407',4)
) AS v(n, c)
ON CONFLICT (numero) DO NOTHING;
