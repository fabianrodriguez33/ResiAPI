package pe.upeu.resi.ResiAPI.dto.response;

import java.time.LocalDate;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Datos personales de la cuenta, comunes a todos los roles. */
public record PerfilResponse(Long id, String username, String nombreCompleto, Rol rol, String dni, LocalDate fechaNacimiento,
                             String email, boolean emailVerificado, String telefono) {
}
