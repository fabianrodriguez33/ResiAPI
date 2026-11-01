package pe.upeu.resi.ResiAPI.dto.response;

import java.time.Instant;

/** DTOs de salida del módulo de residentes, habitaciones y QR. */
public final class ResidenteResponses {

    private ResidenteResponses() {
    }

    public record HabitacionResponse(Long id, String numero, int capacidad, boolean activa, long ocupantes, long disponibles) {
    }

    public record OcupacionResponse(long habitaciones, long capacidadTotal, long ocupados, long disponibles) {
    }

    public record HabitacionResumen(Long id, String numero, String cama) {
    }

    public record ResidenteResponse(Long id, Long usuarioId, String codigo, String nombreCompleto, String telefono, boolean activo,
                                    HabitacionResumen habitacion, boolean puedeSolicitarSolo) {
    }

    public record ApoderadoResponse(Long id, String nombre, String telefono, String email, String parentesco, boolean principal, String usuario) {
    }

    public record QrResponse(String contenido, Instant generadoEn) {
    }
}
