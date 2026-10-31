package pe.upeu.resi.ResiAPI;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj controlable: permite probar horarios de culto, vencimientos de permisos y puertas de garita. */
public class RelojPrueba extends Clock {

    private volatile Instant fijo;

    public void fijar(Instant instante) {
        this.fijo = instante;
    }

    public void liberar() {
        this.fijo = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant f = fijo;
        return f != null ? f : Instant.now();
    }

    @TestConfiguration
    public static class Configuracion {
        @Bean
        @Primary
        RelojPrueba relojPrueba() {
            return new RelojPrueba();
        }
    }
}
