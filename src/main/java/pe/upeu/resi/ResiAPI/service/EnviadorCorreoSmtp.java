package pe.upeu.resi.ResiAPI.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pe.upeu.resi.ResiAPI.exception.Excepciones;

/**
 * Envía el correo por SMTP cuando el servidor lo tiene configurado (variable SPRING_MAIL_HOST y las demás spring.mail.*).
 * Sin SMTP, que es el caso del desarrollo local, el código solo se escribe en el registro del servidor.
 */
@Slf4j
@Service
public class EnviadorCorreoSmtp implements EnviadorCorreo {

    private final ObjectProvider<JavaMailSender> remitente;
    private final String desde;
    private final java.util.Set<String> permitidos;

    /**
     * [permitidos]: correos a los que se puede escribir, separados por coma; vacío = cualquiera. Mientras se prueba el envío
     * real se limita a una cuenta para no molestar a nadie más. [usuarioSmtp] es el remitente por omisión (Gmail y Outlook
     * exigen que coincida con la cuenta que inicia sesión).
     */
    public EnviadorCorreoSmtp(ObjectProvider<JavaMailSender> remitente,
                              @Value("${resi.correo.desde:}") String desde,
                              @Value("${spring.mail.username:}") String usuarioSmtp,
                              @Value("${resi.correo.permitidos:}") String permitidos) {
        this.remitente = remitente;
        this.desde = !desde.isBlank() ? desde : !usuarioSmtp.isBlank() ? usuarioSmtp : "no-responder@resiup.local";
        this.permitidos = java.util.Arrays.stream(permitidos.split(",")).map(String::trim).filter(x -> !x.isEmpty())
                .map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public void enviarCodigo(String destino, String codigo) {
        if (!permitidos.isEmpty() && !permitidos.contains(destino.toLowerCase())) {
            throw new Excepciones.Conflicto("Por ahora solo se pueden verificar los correos autorizados por la administración");
        }
        var sender = remitente.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP sin configurar: el código de verificación para {} es {}", destino, codigo);
            return;
        }
        var mensaje = new SimpleMailMessage();
        mensaje.setFrom(desde);
        mensaje.setTo(destino);
        mensaje.setSubject("ResiUP: tu código de verificación");
        mensaje.setText("Tu código para verificar el correo en ResiUP es " + codigo + ". Vence en 15 minutos. Si no lo pediste, ignora este mensaje.");
        try {
            sender.send(mensaje);
        } catch (MailException e) {
            log.error("No se pudo enviar el correo de verificación", e);
            throw new Excepciones.Conflicto("No se pudo enviar el correo. Inténtalo más tarde.");
        }
    }

    @Override
    public void enviar(String destino, String asunto, String texto) {
        if (destino == null || destino.isBlank() || (!permitidos.isEmpty() && !permitidos.contains(destino.toLowerCase()))) {
            log.info("Correo omitido: el destino no está autorizado");
            return;
        }
        var sender = remitente.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP sin configurar: correo para {}: {} - {}", destino, asunto, texto);
            return;
        }
        var mensaje = new SimpleMailMessage();
        mensaje.setFrom(desde);
        mensaje.setTo(destino);
        mensaje.setSubject("ResiUP: " + asunto);
        mensaje.setText(texto);
        try {
            sender.send(mensaje);
        } catch (MailException e) {
            log.error("No se pudo enviar la notificación por correo", e);
        }
    }
}
