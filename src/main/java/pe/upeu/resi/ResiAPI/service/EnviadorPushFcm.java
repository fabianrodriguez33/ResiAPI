package pe.upeu.resi.ResiAPI.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Push con Firebase Cloud Messaging (API HTTP v1). Se activa con FCM_CREDENCIALES, la ruta al JSON de la cuenta de servicio
 * de Firebase; sin él solo se registra en el log. El token de acceso se firma aquí con la clave de la cuenta de servicio.
 */
@Slf4j
@Service
public class EnviadorPushFcm implements EnviadorPush {

    private final String rutaCredenciales;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private JsonNode cuenta;
    private String accesoActual;
    private Instant accesoVence = Instant.MIN;

    public EnviadorPushFcm(@Value("${resi.push.fcm.credenciales:}") String rutaCredenciales, ObjectMapper json) {
        this.rutaCredenciales = rutaCredenciales;
        this.json = json;
    }

    @Override
    public boolean notificar(String tokenDispositivo, String titulo, String mensaje) {
        if (rutaCredenciales.isBlank()) {
            log.warn("FCM sin configurar: push para un dispositivo: {} - {}", titulo, mensaje);
            return true;
        }
        try {
            var cuerpo = json.writeValueAsString(Map.of("message", Map.of("token", tokenDispositivo,
                    "notification", Map.of("title", titulo, "body", mensaje))));
            var peticion = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/" + cuenta().get("project_id").asString() + "/messages:send"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + tokenDeAcceso())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
            var respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() == 404 || respuesta.statusCode() == 400 && respuesta.body().contains("UNREGISTERED")) {
                return false;
            }
            if (respuesta.statusCode() >= 300) {
                log.error("FCM rechazó el push: HTTP {}", respuesta.statusCode());
            }
        } catch (Exception e) {
            log.error("No se pudo enviar el push", e);
        }
        return true;
    }

    private synchronized JsonNode cuenta() throws Exception {
        if (cuenta == null) cuenta = json.readTree(Files.readString(Path.of(rutaCredenciales)));
        return cuenta;
    }

    private synchronized String tokenDeAcceso() throws Exception {
        if (accesoActual != null && Instant.now().isBefore(accesoVence.minusSeconds(60))) return accesoActual;
        var c = cuenta();
        long ahora = Instant.now().getEpochSecond();
        String cabecera = b64(json.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String carga = b64(json.writeValueAsBytes(Map.of("iss", c.get("client_email").asString(),
                "scope", "https://www.googleapis.com/auth/firebase.messaging", "aud", "https://oauth2.googleapis.com/token",
                "iat", ahora, "exp", ahora + 3600)));
        String pem = c.get("private_key").asString().replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        var firma = Signature.getInstance("SHA256withRSA");
        firma.initSign(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))));
        firma.update((cabecera + "." + carga).getBytes(StandardCharsets.US_ASCII));
        String assertion = cabecera + "." + carga + "." + b64(firma.sign());
        var peticion = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                        + "&assertion=" + assertion)).build();
        var respuesta = json.readTree(http.send(peticion, HttpResponse.BodyHandlers.ofString()).body());
        accesoActual = respuesta.get("access_token").asString();
        accesoVence = Instant.now().plusSeconds(respuesta.get("expires_in").asLong());
        return accesoActual;
    }

    private static String b64(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
}
