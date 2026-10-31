package pe.upeu.resi.ResiAPI.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import pe.upeu.resi.ResiAPI.enums.EstadoSesion;
import pe.upeu.resi.ResiAPI.enums.TipoCulto;

public final class CultoResponses {

    private CultoResponses() {
    }

    public record SesionCultoResponse(Long id, LocalDate fecha, TipoCulto tipo, Instant inicio, Instant corte, EstadoSesion estado, long presentes) {
    }

    public record AsistenciaResponse(Long id, Long sesionId, LocalDate fecha, TipoCulto tipo, Long residenteId, String codigo,
                                     String nombreCompleto, Instant registradoEn, boolean despuesDelCorte) {
    }

    public record ResidentePendienteResponse(Long residenteId, String codigo, String nombreCompleto) {
    }
}
