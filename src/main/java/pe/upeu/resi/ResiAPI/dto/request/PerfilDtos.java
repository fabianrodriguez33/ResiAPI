package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Datos personales que cada usuario edita en su perfil. {@code null} = no cambia; una cadena vacía borra el correo o el teléfono. */
public final class PerfilDtos {

    private PerfilDtos() {
    }

    public record ContactoRequest(
            @Email(message = "Correo inválido") @Size(max = 150) String email,
            @Size(max = 20) String telefono,
            @Past(message = "La fecha de nacimiento debe ser anterior a hoy") LocalDate fechaNacimiento) {
    }

    public record CodigoCorreoRequest(@NotBlank(message = "Escribe el código") @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos") String codigo) {
    }
}
