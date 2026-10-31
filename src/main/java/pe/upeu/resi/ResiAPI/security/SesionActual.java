package pe.upeu.resi.ResiAPI.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Acceso tipado al usuario autenticado de la petición actual. */
@Component
public class SesionActual {

    public record Usuario(Long id, String username, Rol rol) {
    }

    public Usuario obtener() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No hay una sesión autenticada");
        }
        return new Usuario(jwt.getClaim("uid"), jwt.getSubject(), Rol.valueOf(jwt.getClaimAsStringList("roles").get(0)));
    }
}
