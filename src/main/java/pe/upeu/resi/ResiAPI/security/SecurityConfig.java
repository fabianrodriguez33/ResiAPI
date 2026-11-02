package pe.upeu.resi.ResiAPI.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import pe.upeu.resi.ResiAPI.config.AppProperties;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private SecretKey clave(AppProperties props) {
        String secreto = props.jwt().secret();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(AppProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(AppProperties props) {
        return NimbusJwtDecoder.withSecretKey(clave(props)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Convierte el JWT en autoridades y rechaza tokens de usuarios desactivados o eliminados. */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtConverter(UsuarioRepository usuarios) {
        return jwt -> {
            Long uid = jwt.getClaim("uid");
            boolean activo = uid != null && usuarios.findById(uid).map(u -> u.isActivo()).orElse(false);
            if (!activo) {
                throw new DisabledException("Usuario inactivo");
            }
            List<String> roles = jwt.getClaimAsStringList("roles");
            var autoridades = (roles == null ? List.<String>of() : roles).stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            return new JwtAuthenticationToken(jwt, autoridades, jwt.getSubject());
        };
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> conversor) throws Exception {
        AuthenticationEntryPoint noAutenticado = (req, res, ex) -> escribir(res, HttpStatus.UNAUTHORIZED, "Sesión inválida o expirada");
        AccessDeniedHandler sinPermiso = (req, res, ex) -> escribir(res, HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción");
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/olvide-clave").permitAll()
                        .requestMatchers("/api/v1/publico/**", "/publico/**", "/panel", "/panel/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(noAutenticado).accessDeniedHandler(sinPermiso))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(conversor))
                        .authenticationEntryPoint(noAutenticado)
                        .accessDeniedHandler(sinPermiso));
        return http.build();
    }

    private static void escribir(jakarta.servlet.http.HttpServletResponse res, HttpStatus estado, String mensaje) throws java.io.IOException {
        res.setStatus(estado.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + estado.value() + ",\"error\":\"" + estado.getReasonPhrase()
                + "\",\"message\":\"" + mensaje + "\",\"validationErrors\":null}");
    }
}
