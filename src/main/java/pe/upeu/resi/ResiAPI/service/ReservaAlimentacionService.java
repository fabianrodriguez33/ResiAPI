package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.ReservaDtos.ReservaRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.AdvertenciaPermiso;
import pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.EstadoOferta;
import pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.OfertaItem;
import pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.OfertaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.ReservaResponse;
import pe.upeu.resi.ResiAPI.entity.DiaComedor;
import pe.upeu.resi.ResiAPI.entity.ReservaAlimentacion;
import pe.upeu.resi.ResiAPI.entity.SolicitudPermiso;
import pe.upeu.resi.ResiAPI.enums.EstadoReserva;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.DiaComedorRepository;
import pe.upeu.resi.ResiAPI.repository.ReservaAlimentacionRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;
import pe.upeu.resi.ResiAPI.service.HorarioReservas.Ventana;

/**
 * Reservas de alimentación del residente: ver qué puede pedir hoy, reservar, cancelar la suya y ver su historial.
 * Todas las reglas se validan aquí (días, plazo, residente activo, día cerrado, duplicidad); la app solo las muestra.
 * Reservar y cancelar se turnan por fecha de comida con un bloqueo de PostgreSQL, y el índice único de la base es la última barrera
 * contra duplicados (una reserva activa por día y tipo, una sola cena por día).
 */
@Service
@RequiredArgsConstructor
public class ReservaAlimentacionService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final Set<TipoReserva> CENAS = EnumSet.of(TipoReserva.CENA_TAPER, TipoReserva.CENA_SEGUNDO_TURNO);

    private final ReservaAlimentacionRepository reservas;
    private final DiaComedorRepository dias;
    private final ResidenteService residenteService;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    // ------------------------------------------------------------------ oferta

    /** Las comidas que el residente puede pedir hoy y su situación: abierta, vencida, día cerrado o ya reservada. */
    @Transactional(readOnly = true)
    public OfertaResponse oferta() {
        var residente = residenteService.residenteActual();
        Instant ahora = reloj.instant();
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        var items = new ArrayList<OfertaItem>();
        var fechas = new ArrayList<LocalDate>();
        for (TipoReserva tipo : TipoReserva.values()) {
            LocalDate fecha = HorarioReservas.fechaQueSePideHoy(tipo, hoy);
            if (HorarioReservas.seSirve(tipo, fecha) && !fechas.contains(fecha)) {
                fechas.add(fecha);
            }
        }
        var activas = fechas.isEmpty() ? List.<ReservaAlimentacion>of()
                : reservas.findByResidenteIdAndFechaInAndEstado(residente.getId(), fechas, EstadoReserva.RESERVADA);
        for (TipoReserva tipo : TipoReserva.values()) {
            LocalDate fecha = HorarioReservas.fechaQueSePideHoy(tipo, hoy);
            if (!HorarioReservas.seSirve(tipo, fecha)) {
                continue;
            }
            Ventana ventana = HorarioReservas.ventana(tipo, fecha, ahora, zona);
            DiaComedor dia = dias.findById(fecha).filter(DiaComedor::isCerrado).orElse(null);
            var propia = activas.stream().filter(r -> r.getFecha().equals(fecha) && r.getTipo() == tipo).findFirst().orElse(null);
            var delGrupo = activas.stream().filter(r -> r.getFecha().equals(fecha) && grupo(r.getTipo()).contains(tipo)).findFirst().orElse(null);
            EstadoOferta estado = dia != null ? EstadoOferta.DIA_CERRADO : switch (ventana) {
                case ABIERTA -> EstadoOferta.ABIERTA;
                case NO_ABRIO -> EstadoOferta.NO_ABRIO;
                default -> EstadoOferta.VENCIDA;
            };
            String motivoNo = null;
            if (estado == EstadoOferta.DIA_CERRADO) {
                motivoNo = "El comedor está cerrado ese día" + (dia.getMotivo() == null ? "" : ": " + dia.getMotivo());
            } else if (estado == EstadoOferta.NO_ABRIO) {
                motivoNo = "Las reservas todavía no abren";
            } else if (estado == EstadoOferta.VENCIDA) {
                motivoNo = "El plazo terminó a las " + HORA.format(HorarioReservas.cierra(tipo, fecha, zona).atZone(zona));
            } else if (delGrupo != null) {
                motivoNo = tipo.esCena() && delGrupo.getTipo() != tipo ? "Ya tienes una reserva de cena para ese día" : "Ya tienes esta reserva";
            }
            items.add(new OfertaItem(tipo, fecha, HorarioReservas.abre(tipo, fecha, zona), HorarioReservas.cierra(tipo, fecha, zona), estado,
                    motivoNo == null, motivoNo, dia == null ? null : dia.getMotivo(),
                    propia == null ? null : aResponse(propia, null), advertenciaPermiso(residente.getId(), fecha)));
        }
        return new OfertaResponse(hoy, items);
    }

    // ------------------------------------------------------------------ reservar

    @Transactional
    public ReservaResponse reservar(ReservaRequest r) {
        var residente = residenteService.residenteActual();
        if (!residente.isActivo()) {
            throw new Excepciones.Conflicto("El residente no está activo");
        }
        if (r.tipo() == TipoReserva.DESAYUNO_FRIO && r.curso() != null && !r.curso().isBlank()) {
            throw new Excepciones.SolicitudInvalida("Datos inválidos", Map.of("curso", "El desayuno frío no lleva curso o actividad"));
        }
        if (r.motivo() == null || r.motivo().isBlank()) {
            throw new Excepciones.SolicitudInvalida("Datos inválidos", Map.of("motivo", "El motivo es obligatorio"));
        }
        LocalDate fecha = r.fecha();
        if (!HorarioReservas.seSirve(r.tipo(), fecha)) {
            throw new Excepciones.Conflicto("Ese tipo de comida no se sirve ese día");
        }
        reservas.bloquearDia(clave(fecha));
        Instant ahora = reloj.instant();
        dias.findById(fecha).filter(DiaComedor::isCerrado).ifPresent(d -> {
            throw new Excepciones.Conflicto("El comedor está cerrado ese día" + (d.getMotivo() == null ? "" : ": " + d.getMotivo()));
        });
        switch (HorarioReservas.ventana(r.tipo(), fecha, ahora, zona)) {
            case NO_ABRIO -> throw new Excepciones.Conflicto("Todavía no se pueden pedir reservas para esa fecha: abren a las 00:00 del día en que se piden");
            case VENCIDA -> throw new Excepciones.Conflicto("El plazo para reservar terminó a las "
                    + HORA.format(HorarioReservas.cierra(r.tipo(), fecha, zona).atZone(zona)));
            default -> { }
        }
        if (reservas.existeEnTipos(residente.getId(), fecha, EstadoReserva.RESERVADA, grupo(r.tipo()))) {
            throw new Excepciones.Conflicto(r.tipo().esCena()
                    ? "Ya tienes una reserva de cena para ese día; cancélala para reservar la otra"
                    : "Ya tienes esa reserva para ese día");
        }
        var reserva = new ReservaAlimentacion();
        reserva.setResidenteId(residente.getId());
        reserva.setTipo(r.tipo());
        reserva.setFecha(fecha);
        reserva.setMotivo(r.motivo().trim());
        reserva.setCurso(limpiar(r.curso()));
        reserva.setObservacion(limpiar(r.observacion()));
        reserva.setEstado(EstadoReserva.RESERVADA);
        reserva.setCreadaPor(sesion.obtener().id());
        reserva.setCreadaEn(ahora);
        try {
            reserva = reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            // Última barrera: el índice único de la base impide dos reservas activas aunque algo se cruzara.
            throw new Excepciones.Conflicto("Ya tienes una reserva activa para ese día");
        }
        auditoria.registrar(sesion.obtener().id(), "RESERVA_CREADA", "reserva_alimentacion", reserva.getId(), r.tipo() + " " + fecha);
        return aResponse(reserva, advertenciaPermiso(residente.getId(), fecha));
    }

    // ------------------------------------------------------------------ cancelar

    /** El residente cancela la suya, sin motivo, mientras el plazo de ese tipo siga abierto. La reserva se conserva en el historial. */
    @Transactional
    public ReservaResponse cancelar(Long id) {
        var residente = residenteService.residenteActual();
        LocalDate fecha = reservas.fechaDeSuReserva(id, residente.getId())
                .orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Reserva no encontrada"));
        reservas.bloquearDia(clave(fecha));
        var reserva = reservas.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Reserva no encontrada"));
        if (reserva.getEstado() != EstadoReserva.RESERVADA) {
            throw new Excepciones.Conflicto("Esa reserva ya está cancelada");
        }
        Instant ahora = reloj.instant();
        if (HorarioReservas.ventana(reserva.getTipo(), fecha, ahora, zona) == Ventana.VENCIDA) {
            throw new Excepciones.Conflicto("El plazo para cancelar terminó a las "
                    + HORA.format(HorarioReservas.cierra(reserva.getTipo(), fecha, zona).atZone(zona)));
        }
        reserva.setEstado(EstadoReserva.CANCELADA_RESIDENTE);
        reserva.setCanceladaPor(sesion.obtener().id());
        reserva.setCanceladaEn(ahora);
        auditoria.registrar(sesion.obtener().id(), "RESERVA_CANCELADA", "reserva_alimentacion", reserva.getId(), reserva.getTipo() + " " + fecha);
        return aResponse(reserva, null);
    }

    // ------------------------------------------------------------------ historial propio

    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> mias(Pageable pageable) {
        var residente = residenteService.residenteActual();
        return PaginaResponse.de(reservas.findByResidenteIdOrderByFechaDescIdDesc(residente.getId(), pageable), r -> aResponse(r, null));
    }

    // ------------------------------------------------------------------ apoyo

    /** Tipos que no pueden estar activos a la vez con [tipo] el mismo día: las dos cenas se excluyen entre sí. */
    private static Set<TipoReserva> grupo(TipoReserva tipo) {
        return tipo.esCena() ? CENAS : EnumSet.of(tipo);
    }

    /** Clave del bloqueo de PostgreSQL para una fecha de comida. */
    static long clave(LocalDate fecha) {
        return (1L << 40) + fecha.toEpochDay();
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /** Permiso aprobado y no anulado cuya vigencia toca el día de la comida (sin mirar la hora). Si hay varios, destaca el de pernoctar. */
    private AdvertenciaPermiso advertenciaPermiso(Long residenteId, LocalDate fecha) {
        Instant inicio = fecha.atStartOfDay(zona).toInstant();
        Instant fin = fecha.plusDays(1).atStartOfDay(zona).toInstant();
        List<SolicitudPermiso> permisos = reservas.permisosAprobadosQueTocan(residenteId, inicio, fin);
        if (permisos.isEmpty()) {
            return null;
        }
        SolicitudPermiso p = permisos.stream().filter(s -> s.getTipo() == TipoPermiso.PERNOCTE).findFirst().orElse(permisos.get(0));
        String nombre = switch (p.getTipo()) {
            case CORTO -> "corto";
            case LARGO -> "largo";
            case PERNOCTE -> "para pernoctar fuera";
        };
        return new AdvertenciaPermiso("Tienes un permiso " + nombre + " aprobado del " + FECHA_HORA.format(p.getSalidaSolicitada().atZone(zona))
                + " al " + FECHA_HORA.format(p.getRetornoAutorizado().atZone(zona)) + " que coincide con ese día. La reserva se registra de todos modos.",
                p.getTipo(), p.getSalidaSolicitada(), p.getRetornoAutorizado());
    }

    ReservaResponse aResponse(ReservaAlimentacion r, AdvertenciaPermiso advertencia) {
        return new ReservaResponse(r.getId(), r.getTipo(), r.getFecha(), r.getMotivo(), r.getCurso(), r.getObservacion(), r.getEstado(),
                r.getCreadaEn(), r.getCanceladaEn(), r.getMotivoCancelacion(), advertencia);
    }
}
