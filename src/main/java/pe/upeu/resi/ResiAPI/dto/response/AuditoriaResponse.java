package pe.upeu.resi.ResiAPI.dto.response;

import java.time.Instant;

public record AuditoriaResponse(Long id, Long usuarioId, String accion, String entidad, Long entidadId, String detalle, Instant creadoEn) {
}
