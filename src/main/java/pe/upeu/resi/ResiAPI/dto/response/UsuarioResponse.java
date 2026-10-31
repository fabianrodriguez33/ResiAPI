package pe.upeu.resi.ResiAPI.dto.response;

import pe.upeu.resi.ResiAPI.enums.Rol;

public record UsuarioResponse(Long id, String username, String nombreCompleto, Rol rol, boolean activo) {
}
