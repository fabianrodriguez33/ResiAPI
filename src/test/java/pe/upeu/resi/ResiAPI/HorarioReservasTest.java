package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;
import pe.upeu.resi.ResiAPI.service.HorarioReservas;
import pe.upeu.resi.ResiAPI.service.HorarioReservas.Ventana;

class HorarioReservasTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    // Semana de prueba: lunes 2026-11-02 ... domingo 2026-11-08
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    private static Instant lima(LocalDate dia, int h, int m, int s) {
        return LocalDateTime.of(dia, java.time.LocalTime.of(h, m, s)).atZone(LIMA).toInstant();
    }

    @Test
    void losDiasEnQueSeSirveCadaTipo() {
        for (var tipo : new TipoReserva[] {TipoReserva.CENA_TAPER, TipoReserva.CENA_SEGUNDO_TURNO}) {
            for (int d = 0; d <= 3; d++) assertTrue(HorarioReservas.seSirve(tipo, LUNES.plusDays(d)), "lunes a jueves");
            for (int d = 4; d <= 6; d++) assertFalse(HorarioReservas.seSirve(tipo, LUNES.plusDays(d)), "viernes a domingo no");
        }
        for (int d = 0; d <= 4; d++) {
            assertTrue(HorarioReservas.seSirve(TipoReserva.ALMUERZO, LUNES.plusDays(d)), "almuerzo lunes a viernes");
            assertTrue(HorarioReservas.seSirve(TipoReserva.DESAYUNO_FRIO, LUNES.plusDays(d)), "desayuno frío se sirve lunes a viernes");
        }
        for (int d = 5; d <= 6; d++) {
            assertFalse(HorarioReservas.seSirve(TipoReserva.ALMUERZO, LUNES.plusDays(d)));
            assertFalse(HorarioReservas.seSirve(TipoReserva.DESAYUNO_FRIO, LUNES.plusDays(d)));
        }
    }

    @Test
    void laCenaAbreALas00YCierraInclusivoALas1800() {
        var jueves = LUNES.plusDays(3);
        assertEquals(Ventana.NO_ABRIO, HorarioReservas.ventana(TipoReserva.CENA_TAPER, jueves, lima(jueves.minusDays(1), 23, 59, 59), LIMA));
        assertEquals(Ventana.ABIERTA, HorarioReservas.ventana(TipoReserva.CENA_TAPER, jueves, lima(jueves, 0, 0, 0), LIMA));
        assertEquals(Ventana.ABIERTA, HorarioReservas.ventana(TipoReserva.CENA_SEGUNDO_TURNO, jueves, lima(jueves, 18, 0, 0), LIMA));
        assertEquals(Ventana.VENCIDA, HorarioReservas.ventana(TipoReserva.CENA_SEGUNDO_TURNO, jueves, lima(jueves, 18, 0, 1), LIMA));
        assertEquals(Ventana.NO_SE_SIRVE, HorarioReservas.ventana(TipoReserva.CENA_TAPER, LUNES.plusDays(4), lima(LUNES.plusDays(4), 10, 0, 0), LIMA));
    }

    @Test
    void elAlmuerzoCierraInclusivoALas1200() {
        var viernes = LUNES.plusDays(4);
        assertEquals(Ventana.ABIERTA, HorarioReservas.ventana(TipoReserva.ALMUERZO, viernes, lima(viernes, 12, 0, 0), LIMA));
        assertEquals(Ventana.VENCIDA, HorarioReservas.ventana(TipoReserva.ALMUERZO, viernes, lima(viernes, 12, 0, 1), LIMA));
        assertEquals(Ventana.NO_ABRIO, HorarioReservas.ventana(TipoReserva.ALMUERZO, viernes, lima(viernes.minusDays(1), 20, 0, 0), LIMA));
    }

    @Test
    void elDesayunoFrioSePideElDiaAnteriorYCierraALas1800() {
        var lunes = LUNES;
        var domingoAnterior = lunes.minusDays(1);
        // El desayuno del lunes se pide el domingo: abre a las 00:00 del domingo y cierra a las 18:00:00 del domingo.
        assertEquals(domingoAnterior, HorarioReservas.diaDeSolicitud(TipoReserva.DESAYUNO_FRIO, lunes));
        assertEquals(Ventana.NO_ABRIO, HorarioReservas.ventana(TipoReserva.DESAYUNO_FRIO, lunes, lima(domingoAnterior.minusDays(1), 23, 59, 59), LIMA));
        assertEquals(Ventana.ABIERTA, HorarioReservas.ventana(TipoReserva.DESAYUNO_FRIO, lunes, lima(domingoAnterior, 0, 0, 0), LIMA));
        assertEquals(Ventana.ABIERTA, HorarioReservas.ventana(TipoReserva.DESAYUNO_FRIO, lunes, lima(domingoAnterior, 18, 0, 0), LIMA));
        assertEquals(Ventana.VENCIDA, HorarioReservas.ventana(TipoReserva.DESAYUNO_FRIO, lunes, lima(domingoAnterior, 18, 0, 1), LIMA));
        // Ya el mismo lunes no se puede pedir el desayuno de ese lunes.
        assertEquals(Ventana.VENCIDA, HorarioReservas.ventana(TipoReserva.DESAYUNO_FRIO, lunes, lima(lunes, 6, 0, 0), LIMA));
        // El desayuno del viernes se pide el jueves; el del sábado no existe.
        var viernes = LUNES.plusDays(4);
        assertEquals(LUNES.plusDays(3), HorarioReservas.diaDeSolicitud(TipoReserva.DESAYUNO_FRIO, viernes));
        assertEquals(Ventana.NO_SE_SIRVE, HorarioReservas.ventana(TipoReserva.DESAYUNO_FRIO, LUNES.plusDays(5), lima(viernes, 10, 0, 0), LIMA));
    }

    @Test
    void laFechaQueSePideHoy() {
        var jueves = LUNES.plusDays(3);
        assertEquals(jueves, HorarioReservas.fechaQueSePideHoy(TipoReserva.ALMUERZO, jueves));
        assertEquals(jueves, HorarioReservas.fechaQueSePideHoy(TipoReserva.CENA_TAPER, jueves));
        assertEquals(jueves.plusDays(1), HorarioReservas.fechaQueSePideHoy(TipoReserva.DESAYUNO_FRIO, jueves));
    }
}
