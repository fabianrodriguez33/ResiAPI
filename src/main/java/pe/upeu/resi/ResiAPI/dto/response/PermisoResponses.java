package pe.upeu.resi.ResiAPI.dto.response;

import java.time.Instant;
import java.util.List;
import pe.upeu.resi.ResiAPI.enums.EstadoRevision;
import pe.upeu.resi.ResiAPI.enums.EstadoSolicitud;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;

public final class PermisoResponses {

    private PermisoResponses() {
    }

    public record AmpliacionResponse(Long id, Long solicitudId, String motivo, Long evidenciaId, Instant retornoOriginal, Instant retornoNuevo,
                                     EstadoRevision estado, Instant revisadaEn, String comentario, Instant creadoEn) {
    }

    public record JustificacionResponse(Long id, Long solicitudId, String motivo, Long evidenciaId, EstadoRevision estado, Instant revisadaEn,
                                        String comentario, Instant creadoEn) {
    }

    /** {@code ampliaciones} y {@code justificaciones} sólo se incluyen en el detalle. */
    public record SolicitudResponse(Long id, Long residenteId, String residenteCodigo, String residenteNombre, TipoPermiso tipo, String motivo,
                                    String destino, Instant salidaSolicitada, Instant retornoSolicitado, Instant retornoAutorizado,
                                    EstadoSolicitud estado, boolean requiereApoderado, Instant decididoEn, String comentarioDecision,
                                    Instant creadoEn, List<AmpliacionResponse> ampliaciones, List<JustificacionResponse> justificaciones) {
    }

    /** El enlace sólo lo obtiene el personal; se entrega al apoderado por el medio institucional (pendiente de definir). */
    public record EnlaceApoderadoResponse(String token, String ruta, Instant expiraEn, String apoderado) {
    }

    /** Datos mínimos que el apoderado necesita para decidir. */
    public record SolicitudApoderadoResponse(String residente, String tipo, String motivo, String destino, Instant salida, Instant retorno, Instant expiraEn) {
    }

    public record EvidenciaResponse(Long id, String nombreOriginal, String contentType, long tamanio) {
    }
}
