package pe.upeu.resi.ResiAPI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import pe.upeu.resi.ResiAPI.entity.AutorizacionPermiso;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AutorizacionPermisoRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

@ExtendWith(MockitoExtension.class)
class AutorizacionPermisoServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    @Mock
    private AutorizacionPermisoRepository autorizaciones;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private SesionActual sesion;

    private AutorizacionPermisoService servicio;

    @org.junit.jupiter.api.BeforeEach
    void crearServicio() {
        Clock reloj = Clock.fixed(HOY.atStartOfDay(LIMA).plusHours(12).toInstant(), LIMA);
        servicio = new AutorizacionPermisoService(autorizaciones, residentes, auditoria, sesion, reloj, LIMA);
    }

    private void comoAdministrador() {
        when(sesion.obtener()).thenReturn(new SesionActual.Usuario(7L, "admin", Rol.ADMINISTRADOR));
    }

    private static AutorizacionPermiso autorizacion(long id, LocalDate inicio, LocalDate fin, boolean revocada) {
        AutorizacionPermiso a = new AutorizacionPermiso();
        a.setId(id);
        a.setResidenteId(1L);
        a.setTipo(TipoPermiso.CORTO);
        a.setInicio(inicio);
        a.setFin(fin);
        a.setReferencia("HF-" + id);
        a.setRevocada(revocada);
        return a;
    }

    @Test
    void vigenteDelegaEnLaConsultaDelRepositorio() {
        when(autorizaciones.vigente(1L, TipoPermiso.CORTO, HOY)).thenReturn(true);

        assertTrue(servicio.vigente(1L, TipoPermiso.CORTO, HOY));
    }

    @Test
    void registrarParaUnResidenteInexistenteFalla() {
        when(residentes.findById(9L)).thenReturn(Optional.empty());

        assertThrows(Excepciones.RecursoNoEncontrado.class,
                () -> servicio.registrar(9L, TipoPermiso.CORTO, null, null, "HF-1"));
    }

    @Test
    void laReferenciaEsObligatoria() {
        when(residentes.findById(1L)).thenReturn(Optional.of(new Residente()));

        var e = assertThrows(Excepciones.SolicitudInvalida.class,
                () -> servicio.registrar(1L, TipoPermiso.CORTO, null, null, "   "));

        assertTrue(e.getErrores().containsKey("referencia"));
    }

    @Test
    void elFinNoPuedeSerAnteriorAlInicio() {
        when(residentes.findById(1L)).thenReturn(Optional.of(new Residente()));

        var e = assertThrows(Excepciones.SolicitudInvalida.class,
                () -> servicio.registrar(1L, TipoPermiso.LARGO, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 10), "HF-2"));

        assertTrue(e.getErrores().containsKey("fin"));
    }

    @Test
    void registrarSinInicioUsaHoyYGuardaLaReferenciaSinEspacios() {
        when(residentes.findById(1L)).thenReturn(Optional.of(new Residente()));
        when(autorizaciones.save(any(AutorizacionPermiso.class))).thenAnswer(i -> {
            AutorizacionPermiso a = i.getArgument(0);
            a.setId(5L);
            return a;
        });
        comoAdministrador();

        var r = servicio.registrar(1L, TipoPermiso.CORTO, null, null, "  HF-12 ");

        assertEquals(5L, r.id());
        assertEquals(HOY, r.inicio());
        assertEquals("HF-12", r.referencia());
        assertTrue(r.vigente());
        verify(auditoria).registrar(eq(7L), eq("AUTORIZACION_REGISTRADA"), eq("autorizacion_permiso"), eq(5L), eq("CORTO HF-12"));
    }

    @Test
    void revocarUnaAutorizacionInexistenteFalla() {
        when(autorizaciones.findById(99L)).thenReturn(Optional.empty());

        assertThrows(Excepciones.RecursoNoEncontrado.class, () -> servicio.revocar(99L, "motivo"));
    }

    @Test
    void noSePuedeRevocarDosVeces() {
        when(autorizaciones.findById(3L)).thenReturn(Optional.of(autorizacion(3L, HOY, null, true)));

        assertThrows(Excepciones.Conflicto.class, () -> servicio.revocar(3L, "otra vez"));
    }

    @Test
    void revocarExigeMotivo() {
        when(autorizaciones.findById(3L)).thenReturn(Optional.of(autorizacion(3L, HOY, null, false)));

        var e = assertThrows(Excepciones.SolicitudInvalida.class, () -> servicio.revocar(3L, "  "));

        assertTrue(e.getErrores().containsKey("motivo"));
    }

    @Test
    void revocarMarcaLaAutorizacionYDejaDeEstarVigente() {
        var a = autorizacion(3L, HOY, null, false);
        when(autorizaciones.findById(3L)).thenReturn(Optional.of(a));
        comoAdministrador();

        var r = servicio.revocar(3L, "  Fin de la estadía ");

        assertTrue(a.isRevocada());
        assertEquals("Fin de la estadía", a.getMotivoRevocacion());
        assertNotNull(a.getRevocadaEn());
        assertTrue(r.revocada());
        assertFalse(r.vigente());
        verify(auditoria).registrar(eq(7L), eq("AUTORIZACION_REVOCADA"), eq("autorizacion_permiso"), eq(3L), eq("Fin de la estadía"));
    }

    @Test
    void deResidenteMarcaCualesEstanVigentesHoy() {
        var activa = autorizacion(2L, HOY.minusDays(3), null, false);
        var vencida = autorizacion(1L, HOY.minusDays(30), HOY.minusDays(10), false);
        when(autorizaciones.findByResidenteIdOrderByIdDesc(1L)).thenReturn(List.of(activa, vencida));

        var lista = servicio.deResidente(1L);

        assertEquals(2, lista.size());
        assertTrue(lista.get(0).vigente());
        assertFalse(lista.get(1).vigente());
        assertNull(lista.get(0).motivoRevocacion());
    }
}
