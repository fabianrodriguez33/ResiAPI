package pe.upeu.resi.ResiAPI.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Horario en que se pueden ENVIAR solicitudes de permiso (hora de Perú; no es la hora de salida, que es libre). Reglas del 2026-11-04:
 * desde las 5:30 a. m.; domingo a jueves hasta las 10:00 p. m.; viernes hasta las 2:00 p. m.; sábado de 6:30 p. m. a 10:00 p. m.
 */
public final class HorarioEnvio {

    public static final LocalTime APERTURA = LocalTime.of(5, 30);
    public static final LocalTime CIERRE_DOMINGO_A_JUEVES = LocalTime.of(22, 0);
    public static final LocalTime CIERRE_VIERNES = LocalTime.of(14, 0);
    public static final LocalTime APERTURA_SABADO = LocalTime.of(18, 30);
    public static final LocalTime CIERRE_SABADO = LocalTime.of(22, 0);

    public static final String MENSAJE = "Las solicitudes de permiso se reciben de domingo a jueves de 5:30 a. m. a 10:00 p. m., "
            + "los viernes de 5:30 a. m. a 2:00 p. m. y los sábados de 6:30 p. m. a 10:00 p. m. (hora de Perú)";

    private HorarioEnvio() {
    }

    public static boolean abierto(Instant momento, ZoneId zona) {
        ZonedDateTime z = momento.atZone(zona);
        LocalTime hora = z.toLocalTime().withSecond(0).withNano(0);
        DayOfWeek dia = z.getDayOfWeek();
        if (hora.isBefore(APERTURA) && dia != DayOfWeek.SATURDAY) {
            return false;
        }
        return switch (dia) {
            case FRIDAY -> !hora.isAfter(CIERRE_VIERNES);
            case SATURDAY -> !hora.isBefore(APERTURA_SABADO) && !hora.isAfter(CIERRE_SABADO);
            default -> !hora.isAfter(CIERRE_DOMINGO_A_JUEVES);
        };
    }
}
