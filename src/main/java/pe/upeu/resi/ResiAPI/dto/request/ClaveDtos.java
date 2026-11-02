package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ClaveDtos {

    private ClaveDtos() {
    }

    public record OlvideClaveRequest(@NotBlank(message = "El usuario es obligatorio") @Size(max = 50) String username) {
    }

    public record CambiarClaveRequest(
            @NotBlank(message = "Escribe tu contraseña actual") @Size(max = 100) String claveActual,
            @NotBlank(message = "Escribe la nueva contraseña") @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String claveNueva) {
    }
}
