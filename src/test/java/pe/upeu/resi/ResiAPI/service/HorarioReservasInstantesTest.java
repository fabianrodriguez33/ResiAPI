package pe.upeu.resi.ResiAPI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;
import pe.upeu.resi.ResiAPI.service.HorarioReservas.Ventana;

class HorarioReservasInstantesTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate JUEVES = LocalDate.of(2026, 11, 5);
    private static final LocalDate VIERNES = LocalDate.of(2026, 11, 6);

    private static Instant lima(LocalDate dia, LocalTime hora) {
        return LocalDateTime.of(dia, hora).atZone(LIMA).toInstant();
    }

    @Test
    void laCenaAbreYCierraEnElInstanteDeLimaDelMismoDia() {
        assertEquals(lima(JUEVES, LocalTime.MIDNIGHT), HorarioReservas.abre(TipoReserva.CENA_TAPER, JUEVES, LIMA));
        assertEquals(lima(JUEVES, LocalTime.of(18, 0)), HorarioReservas.cierra(TipoReserva.CENA_TAPER, JUEVES, LIMA));
        assertEquals(lima(JUEVES, LocalTime.of(18, 0)), HorarioReservas.cierra(TipoReserva.CENA_SEGUNDO_TURNO, JUEVES, LIMA));
    }

    @Test
    void elAlmuerzoCierraALas1200YNoLaCena() {
        assertEquals(lima(VIERNES, LocalTime.of(12, 0)), HorarioReservas.cierra(TipoReserva.ALMUERZO, VIERNES, LIMA));
        assertEquals(lima(VIERNES, LocalTime.MIDNIGHT), HorarioReservas.abre(TipoReserva.ALMUERZO, VIERNES, LIMA));
    }

    @Test
    void elDesayunoAbreYCierraElDiaAnterior() {
        LocalDate lunes = LocalDate.of(2026, 11, 9);
        LocalDate domingo = lunes.minusDays(1);

        assertEquals(lima(domingo, LocalTime.MIDNIGHT), HorarioReservas.abre(TipoReserva.DESAYUNO_FRIO, lunes, LIMA));
        assertEquals(lima(domingo, LocalTime.of(18, 0)), HorarioReservas.cierra(TipoReserva.DESAYUNO_FRIO, lunes, LIMA));
    }

    @Test
    void laZonaHorariaCambiaElInstanteAbsoluto() {
        ZoneId utc = ZoneId.of("UTC");

        // 00:00 en Lima (UTC-5) son las 05:00 en UTC.
        assertEquals(Instant.parse("2026-11-05T05:00:00Z"), HorarioReservas.abre(TipoReserva.CENA_TAPER, JUEVES, LIMA));
        assertEquals(Instant.parse("2026-11-05T18:00:00Z"), HorarioReservas.cierra(TipoReserva.CENA_TAPER, JUEVES, utc));
    }

    @Test
    void unAhoraConFraccionDeSegundoSeTruncaAntesDeComparar() {
        Instant cierre = HorarioReservas.cierra(TipoReserva.ALMUERZO, VIERNES, LIMA);

        // 12:00:00.900 se cuenta como 12:00:00, que todavía es válido (cierre inclusivo).
        assertEquals(Ventana.ABIERTA, HorarioReservas.ventana(TipoReserva.ALMUERZO, VIERNES, cierre.plusMillis(900), LIMA));
        // 12:00:01.000 ya es VENCIDA.
        assertEquals(Ventana.VENCIDA, HorarioReservas.ventana(TipoReserva.ALMUERZO, VIERNES, cierre.plusSeconds(1), LIMA));
    }

    @Test
    void elDiaDeSolicitudEsElMismoDiaSalvoElDesayuno() {
        assertEquals(JUEVES, HorarioReservas.diaDeSolicitud(TipoReserva.ALMUERZO, JUEVES));
        assertEquals(JUEVES, HorarioReservas.diaDeSolicitud(TipoReserva.CENA_SEGUNDO_TURNO, JUEVES));
        assertEquals(JUEVES.minusDays(1), HorarioReservas.diaDeSolicitud(TipoReserva.DESAYUNO_FRIO, JUEVES));
    }

    @Test
    void elDesayunoSeSirveSoloEntreSemanaYLaCenaNoLosFinesDeSemana() {
        LocalDate sabado = LocalDate.of(2026, 11, 7);
        LocalDate domingo = LocalDate.of(2026, 11, 8);

        assertFalse(HorarioReservas.seSirve(TipoReserva.CENA_TAPER, sabado));
        assertFalse(HorarioReservas.seSirve(TipoReserva.ALMUERZO, domingo));
        assertTrue(HorarioReservas.seSirve(TipoReserva.DESAYUNO_FRIO, VIERNES));
    }
}
