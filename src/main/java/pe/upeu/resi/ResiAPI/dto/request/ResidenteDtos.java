package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de entrada del módulo de residentes, habitaciones y apoderados. */
public final class ResidenteDtos {

    private ResidenteDtos() {
    }

    public record HabitacionRequest(
            @NotBlank(message = "El número es obligatorio") @Size(max = 10, message = "Máximo 10 caracteres") String numero,
            @NotNull(message = "La capacidad es obligatoria") @Min(value = 1, message = "La capacidad mínima es 1")
            @Max(value = 12, message = "La capacidad máxima es 12") Integer capacidad,
            Boolean activa) {
    }

    public record ResidenteRequest(
            @NotBlank(message = "El usuario es obligatorio")
            @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "Use 3 a 50 caracteres: letras, números, punto, guion o guion bajo") String username,
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombreCompleto,
            @NotBlank(message = "El código es obligatorio") @Size(max = 20) String codigo,
            @Size(max = 20) String telefono) {
    }

    public record ResidenteActualizarRequest(@Size(max = 150) String nombreCompleto, @Size(max = 20) String telefono, Boolean activo) {
    }

    public record AsignacionRequest(@NotNull(message = "La habitación es obligatoria") Long habitacionId) {
    }

    public record ApoderadoRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
            @Size(max = 20) String telefono,
            @Email(message = "Correo inválido") @Size(max = 150) String email,
            @Size(max = 50) String parentesco,
            boolean principal) {
    }
}
