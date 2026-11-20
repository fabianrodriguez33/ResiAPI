package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.response.RankingResponse;
import pe.upeu.resi.ResiAPI.dto.response.RankingResponse.Puesto;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.repository.AsistenciaCultoRepository;
import pe.upeu.resi.ResiAPI.repository.EvaluacionLimpiezaRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;

/**
 * Posición del residente entre los residentes activos en lo que va del ciclo: cultos asistidos y promedio de limpieza.
 * Siempre se calcula sobre los registros originales, así que un registro corregido o anulado cambia el puesto al instante.
 * El ciclo sale de la configuración ({@code ciclo.inicio} y {@code ciclo.fin}); si no está, se usa el semestre en curso
 * (I: marzo a julio; II: agosto a fin de febrero).
 * Empates: comparten puesto (1, 1, 3...).
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final ConfiguracionService config;
    private final ResidenteService residenteService;
    private final ResidenteRepository residentes;
    private final AsistenciaCultoRepository asistencias;
    private final EvaluacionLimpiezaRepository evaluaciones;
    private final Clock reloj;
    private final ZoneId zona;

    @Transactional(readOnly = true)
    public RankingResponse miRanking() {
        Long yo = residenteService.residenteActual().getId();
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        // Sin configuración: ciclo I de marzo a julio y ciclo II de agosto a fin de febrero (enero y febrero cierran el II del año anterior).
        int anioII = hoy.getMonthValue() <= 2 ? hoy.getYear() - 1 : hoy.getYear();
        boolean segundo = hoy.getMonthValue() >= 8 || hoy.getMonthValue() <= 2;
        LocalDate desde = fecha("ciclo.inicio").orElse(segundo ? LocalDate.of(anioII, 8, 1) : LocalDate.of(hoy.getYear(), 3, 1));
        LocalDate hasta = fecha("ciclo.fin").orElse(segundo ? LocalDate.of(anioII + 1, 3, 1).minusDays(1) : LocalDate.of(hoy.getYear(), 7, 31));
        List<Long> activos = residentes.findAll().stream().filter(Residente::isActivo).map(Residente::getId).toList();

        Map<Long, Double> cultos = new HashMap<>();
        activos.forEach(id -> cultos.put(id, 0.0));
        for (Object[] fila : asistencias.cultosPorResidente(desde, hasta)) {
            cultos.computeIfPresent((Long) fila[0], (k, v) -> ((Number) fila[1]).doubleValue());
        }
        Map<Long, Double> limpieza = new HashMap<>();
        for (Object[] fila : evaluaciones.promedioPorResidente(desde, hasta)) {
            if (activos.contains((Long) fila[0])) {
                limpieza.put((Long) fila[0], ((Number) fila[1]).doubleValue());
            }
        }
        return new RankingResponse(desde, hasta, puesto(cultos, yo, activos.size()), puesto(limpieza, yo, limpieza.size()));
    }

    /** Puesto = 1 + cuántos tienen un valor mayor; null si el residente no tiene valor (p. ej. aún sin evaluaciones de limpieza). */
    private static Puesto puesto(Map<Long, Double> valores, Long yo, int total) {
        Double mio = valores.get(yo);
        if (mio == null) {
            return new Puesto(null, total, null);
        }
        int posicion = 1 + (int) valores.values().stream().filter(v -> v > mio).count();
        return new Puesto(posicion, total, Math.round(mio * 10) / 10.0);
    }

    private java.util.Optional<LocalDate> fecha(String clave) {
        return config.texto(clave).map(LocalDate::parse);
    }
}
