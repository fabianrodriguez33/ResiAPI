package pe.upeu.resi.ResiAPI.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.upeu.resi.ResiAPI.exception.Excepciones;

/**
 * Frena la adivinanza de claves: 5 fallos seguidos bloquean el usuario 1 minuto; los 5 siguientes, 10 minutos, y así en
 * adelante (10 minutos cada vez). Un ingreso correcto reinicia la cuenta. Se lleva por nombre de usuario, existan o no,
 * para no revelar qué cuentas existen.
 */
@Component
@RequiredArgsConstructor
public class LimitadorLogin {

    static final int INTENTOS = 5;
    static final Duration PRIMER_BLOQUEO = Duration.ofMinutes(1);
    static final Duration BLOQUEO_POSTERIOR = Duration.ofMinutes(10);

    private static final class Estado {
        int fallos;
        int bloqueos;
        Instant libreDesde = Instant.MIN;
        Instant ultimoMovimiento = Instant.MIN;
    }

    private final Clock reloj;
    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    /** Falla con 429 si el usuario sigue bloqueado. */
    public void verificar(String usuario) {
        var estado = estados.get(clave(usuario));
        if (estado == null) return;
        synchronized (estado) {
            var ahora = reloj.instant();
            if (ahora.isBefore(estado.libreDesde)) {
                long segundos = Math.max(1, Duration.between(ahora, estado.libreDesde).plusMillis(999).toSeconds());
                throw new Excepciones.DemasiadosIntentos(segundos);
            }
        }
    }

    public void registrarFallo(String usuario) {
        limpiarViejos();
        var estado = estados.computeIfAbsent(clave(usuario), k -> new Estado());
        synchronized (estado) {
            var ahora = reloj.instant();
            estado.ultimoMovimiento = ahora;
            if (++estado.fallos >= INTENTOS) {
                estado.libreDesde = ahora.plus(estado.bloqueos == 0 ? PRIMER_BLOQUEO : BLOQUEO_POSTERIOR);
                estado.bloqueos++;
                estado.fallos = 0;
            }
        }
    }

    public void registrarAcierto(String usuario) {
        estados.remove(clave(usuario));
    }

    /** Olvida lo que lleva una hora sin tocarse para que el mapa no crezca sin límite. */
    private void limpiarViejos() {
        if (estados.size() < 1000) return;
        var corte = reloj.instant().minus(Duration.ofHours(1));
        estados.values().removeIf(e -> e.ultimoMovimiento.isBefore(corte) && e.libreDesde.isBefore(corte));
    }

    private static String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
