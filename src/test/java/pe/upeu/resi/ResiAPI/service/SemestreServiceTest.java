package pe.upeu.resi.ResiAPI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
import pe.upeu.resi.ResiAPI.entity.AsignacionHabitacion;
import pe.upeu.resi.ResiAPI.entity.Semestre;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsignacionHabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.SemestreRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

@ExtendWith(MockitoExtension.class)
class SemestreServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate INICIO = LocalDate.of(2027, 3, 1);
    private static final LocalDate FIN = LocalDate.of(2027, 7, 31);

    @Mock
    private SemestreRepository semestres;
    @Mock
    private AsignacionHabitacionRepository asignaciones;
    @Mock
    private ConfiguracionService configuracion;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private SesionActual sesion;

    private SemestreService servicio;

    /** Fija "hoy" a mediodía de Lima y crea el servicio con ese reloj. */
    private void enFecha(LocalDate hoy) {
        Clock reloj = Clock.fixed(hoy.atStartOfDay(LIMA).plusHours(12).toInstant(), LIMA);
        servicio = new SemestreService(semestres, asignaciones, configuracion, auditoria, sesion, reloj);
    }

    private void comoAdministrador() {
        when(sesion.obtener()).thenReturn(new SesionActual.Usuario(7L, "admin", Rol.ADMINISTRADOR));
    }

    private static Semestre semestre(long id, String nombre, LocalDate inicio, LocalDate fin, boolean activo) {
        Semestre s = new Semestre();
        s.setId(id);
        s.setNombre(nombre);
        s.setInicio(inicio);
        s.setFin(fin);
        s.setActivo(activo);
        return s;
    }

    /** Stubs comunes de una creación que llega a guardar: el nuevo semestre recibe id 4. */
    private void preparaGuardado() {
        when(semestres.save(any(Semestre.class))).thenAnswer(i -> {
            Semestre s = i.getArgument(0);
            s.setId(4L);
            return s;
        });
        comoAdministrador();
    }

    @Test
    void elNombreDebeTenerElFormatoDelSemestre() {
        enFecha(LocalDate.of(2026, 11, 10));

        var e = assertThrows(Excepciones.SolicitudInvalida.class, () -> servicio.crear("2027x", INICIO, FIN));

        assertTrue(e.getErrores().containsKey("nombre"));
    }

    @Test
    void elFinDebeSerPosteriorAlInicio() {
        enFecha(LocalDate.of(2026, 11, 10));

        var e = assertThrows(Excepciones.SolicitudInvalida.class, () -> servicio.crear("2027-1", INICIO, INICIO));

        assertTrue(e.getErrores().containsKey("fin"));
    }

    @Test
    void sinFechasSeIndicanLosErrores() {
        enFecha(LocalDate.of(2026, 11, 10));

        var e = assertThrows(Excepciones.SolicitudInvalida.class, () -> servicio.crear("2027-1", null, null));

        assertTrue(e.getErrores().containsKey("inicio"));
        assertTrue(e.getErrores().containsKey("fin"));
    }

    @Test
    void unNombreRepetidoSeRechaza() {
        enFecha(LocalDate.of(2026, 11, 10));
        when(semestres.existsByNombreIgnoreCase("2027-1")).thenReturn(true);

        var e = assertThrows(Excepciones.SolicitudInvalida.class, () -> servicio.crear("2027-1", INICIO, FIN));

        assertTrue(e.getErrores().containsKey("nombre"));
    }

    @Test
    void crearCierraElActivoLiberaLasHabitacionesYRegistraLaAuditoria() {
        enFecha(LocalDate.of(2026, 11, 10));
        var anterior = semestre(3L, "2026-2", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), true);
        var vigente = new AsignacionHabitacion();
        when(semestres.existsByNombreIgnoreCase("2027-1")).thenReturn(false);
        when(semestres.findByActivoTrue()).thenReturn(Optional.of(anterior));
        when(semestres.saveAndFlush(any(Semestre.class))).thenAnswer(i -> i.getArgument(0));
        when(asignaciones.findByHastaIsNull()).thenReturn(List.of(vigente));
        preparaGuardado();

        var r = servicio.crear("2027-1", INICIO, FIN);

        assertFalse(anterior.isActivo());
        assertNotNull(anterior.getCerradoEn());
        assertNotNull(vigente.getHasta());
        verify(asignaciones).saveAll(List.of(vigente));
        assertEquals(4L, r.id());
        assertEquals("2027-1", r.nombre());
        assertTrue(r.activo());
        verify(auditoria).registrar(eq(7L), eq("SEMESTRE_CREADO"), eq("semestre"), eq(4L), contains("habitaciones_liberadas=1"));
    }

    @Test
    void crearActualizaLasFechasDelCicloSiEstanConfiguradas() {
        enFecha(LocalDate.of(2026, 11, 10));
        when(semestres.existsByNombreIgnoreCase("2027-1")).thenReturn(false);
        when(asignaciones.findByHastaIsNull()).thenReturn(List.of());
        when(configuracion.texto("ciclo.inicio")).thenReturn(Optional.of("2026-08-01"));
        when(configuracion.texto("ciclo.fin")).thenReturn(Optional.of("2026-12-31"));
        preparaGuardado();

        servicio.crear("2027-1", INICIO, FIN);

        verify(configuracion).actualizar("ciclo.inicio", "2027-03-01", 7L);
        verify(configuracion).actualizar("ciclo.fin", "2027-07-31", 7L);
    }

    @Test
    void terminarSinSemestreActivoEsUnConflicto() {
        enFecha(LocalDate.of(2026, 11, 10));
        when(semestres.findByActivoTrue()).thenReturn(Optional.empty());

        assertThrows(Excepciones.Conflicto.class, () -> servicio.terminarActual());
    }

    @Test
    void terminarAntesDelFinAjustaElFinAHoy() {
        enFecha(LocalDate.of(2026, 11, 10));
        var activo = semestre(2L, "2026-2", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15), true);
        when(semestres.findByActivoTrue()).thenReturn(Optional.of(activo));
        comoAdministrador();

        var r = servicio.terminarActual();

        assertFalse(activo.isActivo());
        assertNotNull(activo.getCerradoEn());
        assertEquals(LocalDate.of(2026, 11, 10), activo.getFin());
        assertEquals(LocalDate.of(2026, 11, 10), r.fin());
        verify(auditoria).registrar(eq(7L), eq("SEMESTRE_TERMINADO"), eq("semestre"), eq(2L), eq("2026-2"));
    }

    @Test
    void terminarAntesDelInicioDejaElFinEnElInicio() {
        enFecha(LocalDate.of(2026, 11, 10));
        var activo = semestre(2L, "2027-1", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 7, 31), true);
        when(semestres.findByActivoTrue()).thenReturn(Optional.of(activo));
        comoAdministrador();

        servicio.terminarActual();

        assertEquals(LocalDate.of(2026, 12, 1), activo.getFin());
    }

    @Test
    void terminarDespuesDelFinNoCambiaElFin() {
        enFecha(LocalDate.of(2026, 11, 10));
        var activo = semestre(2L, "2026-1", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31), true);
        when(semestres.findByActivoTrue()).thenReturn(Optional.of(activo));
        comoAdministrador();

        servicio.terminarActual();

        assertEquals(LocalDate.of(2026, 7, 31), activo.getFin());
    }

    @Test
    void actualSinSemestreActivoNoSeEncuentra() {
        enFecha(LocalDate.of(2026, 11, 10));
        when(semestres.findByActivoTrue()).thenReturn(Optional.empty());

        assertThrows(Excepciones.RecursoNoEncontrado.class, () -> servicio.actual());
    }
}
