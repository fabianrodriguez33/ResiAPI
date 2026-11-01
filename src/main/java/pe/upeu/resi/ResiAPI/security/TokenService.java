package pe.upeu.resi.ResiAPI.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import pe.upeu.resi.ResiAPI.config.AppProperties;
import pe.upeu.resi.ResiAPI.dto.response.LoginResponse;
import pe.upeu.resi.ResiAPI.entity.Usuario;
import pe.upeu.resi.ResiAPI.enums.Rol;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties props;
    private final Clock reloj;

    /** Permisos informativos para el cliente; la autorización real se valida siempre en el servidor. */
    public static List<String> permisosDe(Rol rol) {
        return switch (rol) {
            case RESIDENTE -> List.of("PERFIL_VER", "QR_VER", "PERMISO_SOLICITAR", "ASISTENCIA_VER", "LIMPIEZA_VER");
            case PRECEPTOR -> List.of("RESIDENTE_VER", "ASISTENCIA_REGISTRAR", "LIMPIEZA_REGISTRAR", "PERMISO_REVISAR");
            case GARITA -> List.of("GARITA_VERIFICAR", "GARITA_REGISTRAR");
            case APODERADO -> List.of("HIJOS_VER", "PERMISO_ACEPTAR", "PERMISO_PERNOCTAR");
            case ADMINISTRADOR -> List.of("USUARIO_GESTIONAR", "RESIDENTE_GESTIONAR", "HABITACION_GESTIONAR", "CONFIG_GESTIONAR", "AUDITORIA_VER");
        };
    }

    public LoginResponse emitir(Usuario usuario) {
        Instant ahora = reloj.instant();
        long duracion = props.jwt().expiracionSegundos();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getUsername())
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(duracion))
                .claim("uid", usuario.getId())
                .claim("roles", List.of(usuario.getRol().name()))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", duracion, usuario.getUsername(), usuario.getNombreCompleto(),
                List.of(usuario.getRol().name()), permisosDe(usuario.getRol()));
    }
}
