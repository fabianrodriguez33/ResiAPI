package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import pe.upeu.resi.ResiAPI.entity.EventoDiaComedor;
import pe.upeu.resi.ResiAPI.entity.ReservaAlimentacion;
import pe.upeu.resi.ResiAPI.enums.AccionComedor;
import pe.upeu.resi.ResiAPI.enums.EstadoReserva;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;
import pe.upeu.resi.ResiAPI.repository.EventoDiaComedorRepository;
import pe.upeu.resi.ResiAPI.repository.ReservaAlimentacionRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;

/** Esquema de reservas de alimentación: una reserva activa por día (las cenas comparten la restricción) y un historial que solo crece. */
class ReservasEsquemaIntegracionTest extends PruebaIntegracion {

    @Autowired
    ReservaAlimentacionRepository reservas;
    @Autowired
    EventoDiaComedorRepository eventos;
    @Autowired
    ResidenteRepository residentes;

    private ReservaAlimentacion reserva(long residenteId, long usuarioId, TipoReserva tipo, LocalDate fecha, EstadoReserva estado) {
        var r = new ReservaAlimentacion();
        r.setResidenteId(residenteId);
        r.setTipo(tipo);
        r.setFecha(fecha);
        r.setMotivo("Clases");
        r.setEstado(estado);
        r.setCreadaPor(usuarioId);
        r.setCreadaEn(Instant.now());
        return r;
    }

    @Test
    void unaSolaReservaActivaPorDiaYLasCenasCompartenLaRestriccion() throws Exception {
        long id = crearResidente(tokenDeRol(Rol.ADMINISTRADOR));
        long usuario = residentes.findById(id).orElseThrow().getUsuarioId();
        LocalDate dia = lunesAleatorio();

        reservas.saveAndFlush(reserva(id, usuario, TipoReserva.CENA_TAPER, dia, EstadoReserva.RESERVADA));
        // La otra cena el mismo día: no se permite mientras la primera siga activa.
        assertThrows(DataIntegrityViolationException.class,
                () -> reservas.saveAndFlush(reserva(id, usuario, TipoReserva.CENA_SEGUNDO_TURNO, dia, EstadoReserva.RESERVADA)));
        // La misma cena otra vez, tampoco.
        assertThrows(DataIntegrityViolationException.class,
                () -> reservas.saveAndFlush(reserva(id, usuario, TipoReserva.CENA_TAPER, dia, EstadoReserva.RESERVADA)));
        // Otro tipo de comida el mismo día sí.
        reservas.saveAndFlush(reserva(id, usuario, TipoReserva.ALMUERZO, dia, EstadoReserva.RESERVADA));
        reservas.saveAndFlush(reserva(id, usuario, TipoReserva.DESAYUNO_FRIO, dia, EstadoReserva.RESERVADA));
        // La misma cena otro día sí.
        reservas.saveAndFlush(reserva(id, usuario, TipoReserva.CENA_TAPER, dia.plusDays(1), EstadoReserva.RESERVADA));
    }

    @Test
    void lasReservasCanceladasNoBloqueanUnaNueva() throws Exception {
        long id = crearResidente(tokenDeRol(Rol.ADMINISTRADOR));
        long usuario = residentes.findById(id).orElseThrow().getUsuarioId();
        LocalDate dia = lunesAleatorio();

        var primera = reservas.saveAndFlush(reserva(id, usuario, TipoReserva.CENA_TAPER, dia, EstadoReserva.RESERVADA));
        primera.setEstado(EstadoReserva.CANCELADA_RESIDENTE);
        reservas.saveAndFlush(primera);
        // Con la primera cancelada se puede reservar la otra cena, y la misma, y volver a cancelar sin límite.
        var segunda = reservas.saveAndFlush(reserva(id, usuario, TipoReserva.CENA_SEGUNDO_TURNO, dia, EstadoReserva.RESERVADA));
        segunda.setEstado(EstadoReserva.CANCELADA_PASTOR);
        reservas.saveAndFlush(segunda);
        reservas.saveAndFlush(reserva(id, usuario, TipoReserva.CENA_TAPER, dia, EstadoReserva.RESERVADA));
        assertEquals(3, reservas.findAll().stream().filter(r -> java.util.Objects.equals(r.getResidenteId(), id) && r.getFecha().equals(dia)).count(),
                "las canceladas se conservan en el historial");
    }

    @Test
    void elHistorialDeCierresConservaLosTresEventosSinSobrescribir() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        long usuario = residentes.findById(id).orElseThrow().getUsuarioId();
        LocalDate dia = lunesAleatorio();
        AccionComedor[] secuencia = {AccionComedor.CERRADO, AccionComedor.REABIERTO, AccionComedor.CERRADO};
        for (var accion : secuencia) {
            var e = new EventoDiaComedor();
            e.setFecha(dia);
            e.setAccion(accion);
            e.setMotivo(accion == AccionComedor.CERRADO ? "Feriado" : null);
            e.setUsuarioId(usuario);
            e.setCreadoEn(Instant.now());
            eventos.saveAndFlush(e);
        }
        var historial = eventos.findByFechaOrderByIdAsc(dia);
        assertEquals(3, historial.size());
        assertEquals(java.util.List.of(secuencia), historial.stream().map(EventoDiaComedor::getAccion).toList());
    }
}
