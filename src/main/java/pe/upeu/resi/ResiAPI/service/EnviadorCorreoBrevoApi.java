package pe.upeu.resi.ResiAPI.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import pe.upeu.resi.ResiAPI.exception.Excepciones;

/**
 * Envía el correo por la API HTTPS de Brevo cuando el servidor tiene la variable BREVO_API_KEY. Sirve donde el SMTP saliente
 * está bloqueado (por ejemplo, el plan gratuito de Render): usa el puerto 443. El remitente (CORREO_DESDE) debe estar
 * verificado en Brevo. Sin BREVO_API_KEY no se crea y sigue funcionando el envío por SMTP.
 */
@Slf4j
@Service
@Primary
@ConditionalOnExpression("!'${BREVO_API_KEY:}'.isEmpty()")
public class EnviadorCorreoBrevoApi implements EnviadorCorreo {

    private static final URI URL = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String clave;
    private final String desde;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public EnviadorCorreoBrevoApi(@Value("${BREVO_API_KEY:}") String clave, @Value("${resi.correo.desde:}") String desde) {
        this.clave = clave.trim();
        this.desde = desde.trim();
    }

    @Override
    public void enviarCodigo(String destino, String codigo) {
        boolean ok = mandar(destino, "ResiUP: tu código de verificación",
                "Tu código para verificar el correo en ResiUP es " + codigo + ". Vence en 15 minutos. Si no lo pediste, ignora este mensaje.");
        if (!ok) {
            throw new Excepciones.Conflicto("No se pudo enviar el correo. Inténtalo más tarde.");
        }
    }

    @Override
    public void enviar(String destino, String asunto, String texto) {
        if (destino == null || destino.isBlank()) {
            return;
        }
        mandar(destino, "ResiUP: " + asunto, texto);
    }

    private boolean mandar(String destino, String asunto, String texto) {
        if (desde.isEmpty()) {
            log.error("Falta CORREO_DESDE: no se puede enviar por la API de Brevo");
            return false;
        }
        String cuerpo = "{\"sender\":{\"name\":\"ResiUP\",\"email\":" + json(desde) + "},\"to\":[{\"email\":" + json(destino) + "}],"
                + "\"subject\":" + json(asunto) + ",\"textContent\":" + json(texto) + "}";
        try {
            var peticion = HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(10))
                    .header("api-key", clave).header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
            var respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                log.error("Brevo rechazó el correo: HTTP {} {}", respuesta.statusCode(), respuesta.body());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Envío por Brevo interrumpido", e);
            return false;
        } catch (java.io.IOException | RuntimeException e) {
            log.error("No se pudo enviar el correo por la API de Brevo", e);
            return false;
        }
    }

    /** Cadena JSON entre comillas, con los caracteres especiales escapados. */
    static String json(String s) {
        var sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
