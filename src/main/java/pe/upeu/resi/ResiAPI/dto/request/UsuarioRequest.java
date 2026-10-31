package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.upeu.resi.ResiAPI.enums.Rol;

public record UsuarioRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "Use 3 a 50 caracteres: letras, números, punto, guion o guion bajo") String username,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password,
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombreCompleto,
        @NotNull(message = "El rol es obligatorio") Rol rol) {
}
