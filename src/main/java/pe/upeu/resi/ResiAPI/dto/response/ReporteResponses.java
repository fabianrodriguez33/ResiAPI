package pe.upeu.resi.ResiAPI.dto.response;

/** Notas del mes de cultos y de limpieza. Las notas son AD, A, B o C; {@code notaLimpieza} es null si no hubo evaluaciones. */
public final class ReporteResponses {

    private ReporteResponses() {
    }

    /**
     * Resumen del mes. {@code metodologia}: {@code POR_DIAS} (desde noviembre de 2026: días asistidos entre días con culto programado,
     * con {@code diasAsistidos}, {@code diasConCulto}, {@code faltasJustificadas} y {@code porcentajeAsistencia}) o {@code ANTERIOR}
     * (meses previos: escala por cantidad de cultos, esos cuatro campos vacíos). {@code notaCultos} es nula cuando {@code sinCultos}.
     * {@code notaProvisional}: el mes aún no termina, es una «nota a la fecha».
     */
    public record ResumenMensualResponse(int anio, int mes, long cultos, String notaCultos, long tardanzas,
                                         long evaluaciones, Double promedioLimpieza, String notaLimpieza,
                                         String metodologia, Long diasAsistidos, Long diasConCulto, Long faltasJustificadas,
                                         Double porcentajeAsistencia, boolean sinCultos, boolean notaProvisional) {
    }

    public record FilaReporteResponse(Long residenteId, String codigo, String nombreCompleto, String habitacion, ResumenMensualResponse resumen) {
    }
}
