package pe.upeu.resi.ResiAPI.dto.response;

/** Notas del mes de cultos y de limpieza. Las notas son AD, A, B o C; {@code notaLimpieza} es null si no hubo evaluaciones. */
public final class ReporteResponses {

    private ReporteResponses() {
    }

    public record ResumenMensualResponse(int anio, int mes, long cultos, String notaCultos, long tardanzas,
                                         long evaluaciones, Double promedioLimpieza, String notaLimpieza) {
    }

    public record FilaReporteResponse(Long residenteId, String codigo, String nombreCompleto, String habitacion, ResumenMensualResponse resumen) {
    }
}
