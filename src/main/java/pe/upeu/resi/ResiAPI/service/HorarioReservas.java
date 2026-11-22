package pe.upeu.resi.ResiAPI.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;

/**
 * Días y plazos de las reservas de alimentación (hora de Perú). {@code fecha} es siempre el día en que se sirve la comida.
 * Cenas: lunes a jueves, mismo día, hasta las 18:00:00. Almuerzo: lunes a viernes, mismo día, hasta las 12:00:00.
 * Desayuno frío: lunes a viernes, se pide el día anterior (domingo a jueves) hasta las 18:00:00.
 * Las reservas abren a las 00:00 del día en que se piden y cierran de forma inclusiva en la hora indicada.
 */
public final class HorarioReservas {

    public static final LocalTime CIERRE_CENA = LocalTime.of(18, 0, 0);
    public static final LocalTime CIERRE_ALMUERZO = LocalTime.of(12, 0, 0);
    public static final LocalTime CIERRE_DESAYUNO = LocalTime.of(18, 0, 0);

    /** Situación de un tipo de comida de una fecha en un momento dado (sin considerar si el pastor cerró el día). */
    public enum Ventana {
        NO_SE_SIRVE, NO_ABRIO, ABIERTA, VENCIDA
    }

    private HorarioReservas() {
    }

    /** Si ese tipo de comida se sirve en esa fecha (según el día de la semana). */
    public static boolean seSirve(TipoReserva tipo, LocalDate fecha) {
        DayOfWeek d = fecha.getDayOfWeek();
        return switch (tipo) {
            case CENA_TAPER, CENA_SEGUNDO_TURNO -> d.getValue() >= DayOfWeek.MONDAY.getValue() && d.getValue() <= DayOfWeek.THURSDAY.getValue();
            case ALMUERZO, DESAYUNO_FRIO -> d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
        };
    }

    /** Día en que se pide esa comida: el mismo día, salvo el desayuno frío, que se pide el día anterior. */
    public static LocalDate diaDeSolicitud(TipoReserva tipo, LocalDate fecha) {
        return tipo == TipoReserva.DESAYUNO_FRIO ? fecha.minusDays(1) : fecha;
    }

    /** Fecha de la comida que se puede pedir hoy: la de hoy, o la de mañana para el desayuno frío. */
    public static LocalDate fechaQueSePideHoy(TipoReserva tipo, LocalDate hoy) {
        return tipo == TipoReserva.DESAYUNO_FRIO ? hoy.plusDays(1) : hoy;
    }

    public static Instant abre(TipoReserva tipo, LocalDate fecha, ZoneId zona) {
        return diaDeSolicitud(tipo, fecha).atStartOfDay(zona).toInstant();
    }

    /** Último instante (inclusive, a la precisión de segundos) en que se acepta la reserva. */
    public static Instant cierra(TipoReserva tipo, LocalDate fecha, ZoneId zona) {
        LocalTime hora = switch (tipo) {
            case CENA_TAPER, CENA_SEGUNDO_TURNO -> CIERRE_CENA;
            case ALMUERZO -> CIERRE_ALMUERZO;
            case DESAYUNO_FRIO -> CIERRE_DESAYUNO;
        };
        return diaDeSolicitud(tipo, fecha).atTime(hora).atZone(zona).toInstant();
    }

    public static Ventana ventana(TipoReserva tipo, LocalDate fecha, Instant ahora, ZoneId zona) {
        if (!seSirve(tipo, fecha)) {
            return Ventana.NO_SE_SIRVE;
        }
        Instant segundo = ahora.truncatedTo(ChronoUnit.SECONDS);
        if (segundo.isBefore(abre(tipo, fecha, zona))) {
            return Ventana.NO_ABRIO;
        }
        return segundo.isAfter(cierra(tipo, fecha, zona)) ? Ventana.VENCIDA : Ventana.ABIERTA;
    }
}
