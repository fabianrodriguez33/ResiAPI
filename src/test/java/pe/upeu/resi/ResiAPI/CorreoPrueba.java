package pe.upeu.resi.ResiAPI;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pe.upeu.resi.ResiAPI.service.EnviadorCorreo;

/** Guarda el último código enviado a cada correo para poder confirmarlo en las pruebas. */
public class CorreoPrueba implements EnviadorCorreo {

    private final Map<String, String> ultimos = new ConcurrentHashMap<>();

    @Override
    public void enviarCodigo(String destino, String codigo) {
        ultimos.put(destino.toLowerCase(), codigo);
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
