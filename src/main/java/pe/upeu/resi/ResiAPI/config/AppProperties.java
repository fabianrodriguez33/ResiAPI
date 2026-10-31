package pe.upeu.resi.ResiAPI.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resi")
public record AppProperties(Jwt jwt, Qr qr, Admin admin, Evidencias evidencias, String zonaHoraria) {

    public record Jwt(String secret, long expiracionSegundos) {
    }

    public record Qr(String secreto) {
    }

    public record Admin(String usuario, String clave) {
    }

    public record Evidencias(String directorio) {
    }
}
