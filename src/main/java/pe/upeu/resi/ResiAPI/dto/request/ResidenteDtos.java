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
            @NotBlank(message = "El código universitario es obligatorio")
            @Pattern(regexp = "\\d{9}", message = "El código universitario tiene 9 dígitos (ej. 202410354)") String codigo,
            @NotBlank(message = "El DNI es obligatorio") @Pattern(regexp = "\\d{8}", message = "El DNI tiene 8 dígitos") String dni,
            @jakarta.validation.constraints.Past(message = "La fecha de nacimiento debe ser anterior a hoy") java.time.LocalDate fechaNacimiento,
            @Size(max = 20) String telefono) {
    }

    /**
     * Datos que el propio residente puede editar. {@code null} = no cambia; una cadena vacía borra el dato (correo, teléfono, carrera).
     * El código universitario y el DNI los cambia solo la administración.
     */
    public record PerfilRequest(
            @Email(message = "Correo inválido") @Size(max = 150) String email,
            @Size(max = 20) String telefono,
            @jakarta.validation.constraints.Past(message = "La fecha de nacimiento debe ser anterior a hoy") java.time.LocalDate fechaNacimiento,
            @Size(max = 100, message = "Máximo 100 caracteres") String carrera,
            @Min(value = 1, message = "El ciclo va de 1 a 14") @Max(value = 14, message = "El ciclo va de 1 a 14") Integer ciclo) {
    }

    public record ResidenteActualizarRequest(@Size(max = 150) String nombreCompleto, @Size(max = 20) String telefono, Boolean activo,
                                             Boolean puedeSolicitarSolo,
                                             @Pattern(regexp = "\\d{9}", message = "El código universitario tiene 9 dígitos (ej. 202410354)") String codigo,
                                             @Pattern(regexp = "\\d{8}", message = "El DNI tiene 8 dígitos") String dni,
                                             @jakarta.validation.constraints.Past(message = "La fecha de nacimiento debe ser anterior a hoy") java.time.LocalDate fechaNacimiento) {
    }

    public record AsignacionRequest(@NotNull(message = "La habitación es obligatoria") Long habitacionId, @Size(max = 1) String cama) {
    }

    public record ApoderadoRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
            @Size(max = 20) String telefono,
            @Email(message = "Correo inválido") @Size(max = 150) String email,
            @Size(max = 50) String parentesco,
            boolean principal,
            /** Opcional: crea (o reutiliza, si ya existe) la cuenta del apoderado para que use la app. */
            @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "Use 3 a 50 caracteres: letras, números, punto, guion o guion bajo") String username,
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password) {
    }
}
