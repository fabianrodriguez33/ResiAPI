package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;

public final class PermisoDtos {

    private PermisoDtos() {
    }

    public record SolicitudRequest(
            @NotNull(message = "El tipo de permiso es obligatorio") TipoPermiso tipo,
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 300, message = "Máximo 300 caracteres") String motivo,
            @NotBlank(message = "El destino es obligatorio") @Size(max = 200, message = "Máximo 200 caracteres") String destino,
            @NotNull(message = "La fecha y hora de salida es obligatoria") Instant salida,
            @NotNull(message = "La fecha y hora de retorno es obligatoria") Instant retorno,
            /** Solo lo envía el apoderado: a qué hijo corresponde el permiso. */
            Long residenteId,
            /** Solo para PERNOCTE: quien recibe al residente, su celular y con quién viaja. */
            @Size(max = 150, message = "Máximo 150 caracteres") String responsableNombre,
            @Size(max = 30, message = "Máximo 30 caracteres") String responsableCelular,
            @Size(max = 150, message = "Máximo 150 caracteres") String acompanante,
            /** Clave generada por la app al pedir sin conexión: evita duplicar la solicitud si el envío se repite. */
            @Size(max = 40) String claveCliente,
            /** Cuándo se creó la solicitud en el teléfono (si se pidió sin conexión): con eso se evalúa el horario de envío. */
            Instant creadaEn) {
    }

    public record DecisionRequest(@NotNull(message = "Indique si se aprueba o se rechaza") Boolean aprobar,
                                  @Size(max = 300, message = "Máximo 300 caracteres") String comentario) {
    }

    public record AmpliacionRequest(
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo,
            @NotNull(message = "El nuevo horario de retorno es obligatorio") Instant retornoNuevo,
            Long evidenciaId) {
    }

    public record JustificacionRequest(
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo,
            Long evidenciaId) {
    }

    public record AnulacionRequest(@NotBlank(message = "Escribe el motivo de la anulación") @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    public record DecisionApoderadoRequest(@NotNull(message = "Indique si se aprueba o se rechaza") Boolean aprobar) {
    }
}
