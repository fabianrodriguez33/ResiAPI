package pe.upeu.resi.ResiAPI;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pe.upeu.resi.ResiAPI.service.EnviadorCorreo;
import pe.upeu.resi.ResiAPI.service.EnviadorPush;
import pe.upeu.resi.ResiAPI.service.EnviadorSms;

/** Guarda lo que saldría por correo, SMS y push para poder comprobarlo en las pruebas. */
public class CorreoPrueba implements EnviadorCorreo, EnviadorSms, EnviadorPush {

    public record Mensaje(String destino, String titulo, String texto) {
    }

    private final Map<String, String> ultimos = new ConcurrentHashMap<>();
    public final List<Mensaje> correos = new CopyOnWriteArrayList<>();
    public final List<Mensaje> sms = new CopyOnWriteArrayList<>();
    public final List<Mensaje> push = new CopyOnWriteArrayList<>();

    @Override
    public void enviarCodigo(String destino, String codigo) {
        ultimos.put(destino.toLowerCase(), codigo);
    }

    @Override
    public void enviar(String destino, String asunto, String texto) {
        correos.add(new Mensaje(destino.toLowerCase(), asunto, texto));
    }

    @Override
    public void enviar(String telefono, String texto) {
        sms.add(new Mensaje(telefono, "", texto));
    }

    @Override
    public boolean notificar(String tokenDispositivo, String titulo, String mensaje) {
        push.add(new Mensaje(tokenDispositivo, titulo, mensaje));
        return true;
    }

    public String ultimoCodigo(String destino) {
        return ultimos.get(destino.toLowerCase());
    }

    @TestConfiguration
    public static class Configuracion {
        @Bean
        @Primary
        CorreoPrueba correoPrueba() {
            return new CorreoPrueba();
        }
    }
}
