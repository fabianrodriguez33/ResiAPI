package pe.upeu.resi.ResiAPI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.upeu.resi.ResiAPI.dto.response.RankingResponse;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsistenciaCultoRepository;
import pe.upeu.resi.ResiAPI.repository.EvaluacionLimpiezaRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock
    private ConfiguracionService config;
    @Mock
    private ResidenteService residenteService;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private AsistenciaCultoRepository asistencias;
    @Mock
    private EvaluacionLimpiezaRepository evaluaciones;

    private RankingService servicio;

    /** Fija el día de "hoy" (a mediodía, hora de Lima) y crea el servicio con ese reloj. */
    private void enFecha(LocalDate hoy) {
        Clock reloj = Clock.fixed(hoy.atStartOfDay(LIMA).plusHours(12).toInstant(), LIMA);
        servicio = new RankingService(config, residenteService, residentes, asistencias, evaluaciones, reloj, LIMA);
    }

    private void comoResidente(long id) {
        when(residenteService.residenteActual()).thenReturn(residente(id, true));
    }

    private void residentesActivos(Residente... rs) {
        when(residentes.findAll()).thenReturn(List.of(rs));
    }

    private static Residente residente(long id, boolean activo) {
        Residente r = new Residente();
        r.setId(id);
        r.setActivo(activo);
        return r;
    }

    @Test
    void sinConfiguracionEnNoviembreUsaElCicloIIDelAnio() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);
        residentesActivos(residente(1L, true));

        servicio.miRanking();

        verify(asistencias).cultosPorResidente(LocalDate.of(2026, 8, 1), LocalDate.of(2027, 2, 28));
        verify(evaluaciones).promedioPorResidente(LocalDate.of(2026, 8, 1), LocalDate.of(2027, 2, 28));
    }

    @Test
    void enEneroElCicloIISigueSiendoElDelAnioAnterior() {
        enFecha(LocalDate.of(2027, 1, 15));
        comoResidente(1L);
        residentesActivos(residente(1L, true));

        servicio.miRanking();

        verify(asistencias).cultosPorResidente(LocalDate.of(2026, 8, 1), LocalDate.of(2027, 2, 28));
    }

    @Test
    void enMayoElCicloIVaDeMarzoAJulio() {
        enFecha(LocalDate.of(2026, 5, 10));
        comoResidente(1L);
        residentesActivos(residente(1L, true));

        servicio.miRanking();

        verify(asistencias).cultosPorResidente(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    void laConfiguracionDelCicloTienePrioridadSobreLasFechasPorDefecto() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);
        residentesActivos(residente(1L, true));
        when(config.texto("ciclo.inicio")).thenReturn(Optional.of("2026-03-01"));
        when(config.texto("ciclo.fin")).thenReturn(Optional.of("2026-07-31"));

        servicio.miRanking();

        verify(asistencias).cultosPorResidente(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    void elPuestoSeCalculaContandoCuantosTienenValorMayor() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);
        residentesActivos(residente(1L, true), residente(2L, true), residente(3L, true), residente(4L, true));
        when(asistencias.cultosPorResidente(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] {1L, 5L}, new Object[] {2L, 8L}, new Object[] {3L, 5L}, new Object[] {4L, 3L}));
        when(evaluaciones.promedioPorResidente(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] {1L, 8.25}, new Object[] {2L, 9.0}, new Object[] {3L, 8.25}));

        RankingResponse r = servicio.miRanking();

        // Cultos: solo el residente 2 tiene más (8), así que el 1 queda segundo; empatan el 1 y el 3.
        assertEquals(2, r.cultos().posicion());
        assertEquals(4, r.cultos().total());
        assertEquals(5.0, r.cultos().valor().doubleValue());
        // Limpieza: el 2 tiene 9.0; el 1 y el 3 empatan con 8.25 y el promedio se redondea a un decimal.
        assertEquals(2, r.limpieza().posicion());
        assertEquals(3, r.limpieza().total());
        assertEquals(8.3, r.limpieza().valor().doubleValue());
    }

    @Test
    void empatesCompartenPuesto() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(3L);
        residentesActivos(residente(1L, true), residente(2L, true), residente(3L, true));
        when(asistencias.cultosPorResidente(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] {1L, 7L}, new Object[] {2L, 7L}, new Object[] {3L, 7L}));

        RankingResponse r = servicio.miRanking();

        assertEquals(1, r.cultos().posicion());
    }

    @Test
    void sinEvaluacionesDeLimpiezaElPuestoDeLimpiezaQuedaVacio() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);
        residentesActivos(residente(1L, true));
        when(asistencias.cultosPorResidente(any(), any())).thenReturn(List.<Object[]>of(new Object[] {1L, 5L}));

        RankingResponse r = servicio.miRanking();

        assertNull(r.limpieza().posicion());
        assertNull(r.limpieza().valor());
        assertEquals(0, r.limpieza().total());
    }

    @Test
    void losResidentesInactivosNoEntranAlRanking() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);
        residentesActivos(residente(1L, true), residente(9L, false));
        when(asistencias.cultosPorResidente(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] {1L, 5L}, new Object[] {9L, 20L}));

        RankingResponse r = servicio.miRanking();

        assertEquals(1, r.cultos().posicion());
        assertEquals(1, r.cultos().total());
    }

    @Test
    void unRangoConFinAntesDelInicioEsInvalido() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);

        assertThrows(Excepciones.SolicitudInvalida.class,
                () -> servicio.miRanking(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)));
    }

    @Test
    void unRangoDeMasDeUnAnioEsInvalido() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);

        assertThrows(Excepciones.SolicitudInvalida.class,
                () -> servicio.miRanking(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void unRangoValidoReemplazaElCicloPorDefecto() {
        enFecha(LocalDate.of(2026, 11, 10));
        comoResidente(1L);

        RankingResponse r = servicio.miRanking(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        verify(asistencias).cultosPorResidente(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertEquals(LocalDate.of(2026, 9, 1), r.desde());
        assertEquals(LocalDate.of(2026, 9, 30), r.hasta());
    }
}
