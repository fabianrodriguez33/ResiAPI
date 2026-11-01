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

    public record ResidenteLimpieza(Long residenteId, String codigo, String nombreCompleto, String cama, boolean evaluado) {
    }

    /** Habitación con sus ocupantes y si ya fueron evaluados en la fecha consultada. */
    public record HabitacionLimpiezaResponse(Long habitacionId, String numero, int capacidad, java.util.List<ResidenteLimpieza> residentes, long evaluados) {
    }

    /** Promedio sobre 20 puntos; null si todavía no hay evaluaciones. */
    public record ResumenLimpiezaResponse(long evaluaciones, Double promedio, Integer ultimoTotal, LocalDate ultimaFecha) {
    }
}
