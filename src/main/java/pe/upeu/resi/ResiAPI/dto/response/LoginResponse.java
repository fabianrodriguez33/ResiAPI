package pe.upeu.resi.ResiAPI.dto.response;

import java.util.List;

public record LoginResponse(String token, String tipo, long expiraEnSegundos, String username, String nombreCompleto,
                            List<String> roles, List<String> permisos, boolean debeCambiarClave) {
}
