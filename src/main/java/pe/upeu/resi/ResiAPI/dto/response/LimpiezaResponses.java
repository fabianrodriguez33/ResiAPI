package pe.upeu.resi.ResiAPI.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import pe.upeu.resi.ResiAPI.enums.CriterioLimpieza;

public final class LimpiezaResponses {

    private LimpiezaResponses() {
    }

    public record EvaluacionResponse(Long id, Long residenteId, LocalDate fecha, int total, int maximo, Map<CriterioLimpieza, Integer> puntajes,
                                     String observacion, Long evaluadorId, Instant creadoEn) {
    }

    /** Promedio sobre 20 puntos; null si todavía no hay evaluaciones. */
    public record ResumenLimpiezaResponse(long evaluaciones, Double promedio, Integer ultimoTotal, LocalDate ultimaFecha) {
    }
}
