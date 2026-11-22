-- Los rankings siguen usando las fechas de la configuracion si el administrador las fija; se quita el valor sembrado por V20 para no cambiar su calculo.
DELETE FROM configuracion WHERE clave IN ('ciclo.inicio', 'ciclo.fin') AND valor IN ('2026-08-01', '2026-12-31');
