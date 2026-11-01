package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.Size;

/** Todos los campos son opcionales; sólo se cambian los que llegan informados. */
public record UsuarioActualizarRequest(
        @Size(max = 150) String nombreCompleto,
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password,
        Boolean activo,
        @jakarta.validation.constraints.Pattern(regexp = "\\d{8}", message = "El DNI tiene 8 dígitos") String dni,
        @jakarta.validation.constraints.Past(message = "La fecha de nacimiento debe ser anterior a hoy") java.time.LocalDate fechaNacimiento) {
}
