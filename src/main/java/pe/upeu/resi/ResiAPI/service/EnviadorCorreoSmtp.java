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

    public EnviadorCorreoSmtp(ObjectProvider<JavaMailSender> remitente, @Value("${resi.correo.desde:no-responder@resiup.local}") String desde) {
        this.remitente = remitente;
        this.desde = desde;
    }

    @Override
    public void enviarCodigo(String destino, String codigo) {
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
}
