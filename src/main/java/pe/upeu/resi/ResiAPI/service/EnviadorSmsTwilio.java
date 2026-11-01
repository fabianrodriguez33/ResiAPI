package pe.upeu.resi.ResiAPI.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SMS con la API de Twilio. Se activa con TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_DESDE; sin ellas solo se registra
 * en el log. SMS_PERMITIDOS limita los números a los que se escribe (vacío = cualquiera), igual que los correos.
 */
@Slf4j
@Service
public class EnviadorSmsTwilio implements EnviadorSms {

    private final String sid;
    private final String token;
    private final String desde;
    private final Set<String> permitidos;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public EnviadorSmsTwilio(@Value("${resi.sms.twilio.sid:}") String sid, @Value("${resi.sms.twilio.token:}") String token,
                             @Value("${resi.sms.twilio.desde:}") String desde, @Value("${resi.sms.permitidos:}") String permitidos) {
        this.sid = sid;
        this.token = token;
        this.desde = desde;
        this.permitidos = Arrays.stream(permitidos.split(",")).map(EnviadorSmsTwilio::normalizar).filter(x -> !x.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Deja solo dígitos y el + inicial; un número peruano de 9 dígitos se completa con +51. */
    static String normalizar(String telefono) {
        if (telefono == null) return "";
        String limpio = telefono.replaceAll("[^0-9+]", "");
        if (limpio.matches("9[0-9]{8}")) return "+51" + limpio;
        return limpio;
    }

    @Override
    public void enviar(String telefono, String texto) {
        String destino = normalizar(telefono);
        if (destino.isEmpty() || (!permitidos.isEmpty() && !permitidos.contains(destino))) {
            log.info("SMS omitido: el número no está autorizado");
            return;
        }
        if (sid.isBlank() || token.isBlank() || desde.isBlank()) {
            log.warn("Twilio sin configurar: SMS para {}: {}", destino, texto);
            return;
        }
        try {
            String cuerpo = "To=" + URLEncoder.encode(destino, StandardCharsets.UTF_8)
                    + "&From=" + URLEncoder.encode(desde, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
            var peticion = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
            var respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                log.error("Twilio rechazó el SMS: HTTP {}", respuesta.statusCode());
            }
        } catch (Exception e) {
            log.error("No se pudo enviar el SMS", e);
        }
    }
}
