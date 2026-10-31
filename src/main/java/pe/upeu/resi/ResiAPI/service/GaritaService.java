package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.response.GaritaResponses.MovimientoResponse;
import pe.upeu.resi.ResiAPI.dto.response.GaritaResponses.ResidenteGarita;
import pe.upeu.resi.ResiAPI.dto.response.GaritaResponses.Resultado;
import pe.upeu.resi.ResiAPI.dto.response.GaritaResponses.VerificacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.entity.MovimientoGarita;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.entity.SolicitudPermiso;
import pe.upeu.resi.ResiAPI.enums.TipoMovimiento;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsignacionHabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.HabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.MovimientoGaritaRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.SolicitudPermisoRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/**
 * Control de salidas y retornos. El servidor es la única autoridad: nada se autoriza con datos locales del móvil ni sin
 * conexión. Leer un QR sólo permite consultar; el movimiento se registra con una segunda llamada que revalida todo.
 */
@Service
@RequiredArgsConstructor
public class GaritaService {

    private final QrService qr;
    private final ResidenteRepository residentes;
    private final UsuarioRepository usuarios;
    private final SolicitudPermisoRepository solicitudes;
    private final MovimientoGaritaRepository movimientos;
    private final AsignacionHabitacionRepository asignaciones;
    private final HabitacionRepository habitaciones;
    private final ResidenteService residenteService;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;

    /** Evaluación sin efectos: indica qué movimiento correspondería y por qué. */
    @Transactional(readOnly = true)
    public VerificacionResponse verificar(String contenidoQr) {
        return evaluar(contenidoQr).respuesta();
    }

    @Transactional
    public MovimientoResponse registrar(String contenidoQr, TipoMovimiento tipo) {
        // Se bloquea al residente para serializar escaneos simultáneos del mismo QR (evita dobles salidas).
        var previa = qr.resolver(contenidoQr);
        if (previa.residente() != null) {
            residentes.findByIdForUpdate(previa.residente().getId());
        }
        var ev = evaluar(contenidoQr);
        var v = ev.respuesta();
        if (v.movimientoPermitido() == null) {
            throw new Excepciones.Conflicto("No autorizado: " + v.mensaje());
        }
        if (v.movimientoPermitido() != tipo) {
            throw new Excepciones.Conflicto(tipo == TipoMovimiento.SALIDA
                    ? "El residente ya registró su salida y no ha regresado"
                    : "El residente no tiene una salida abierta: no se puede registrar un retorno");
        }
        Instant ahora = reloj.instant();
        var m = new MovimientoGarita();
        m.setResidenteId(ev.residente().getId());
        m.setSolicitudId(v.solicitudId());
        m.setTipo(tipo);
        m.setRegistradoEn(ahora);
        m.setRegistradoPor(sesion.obtener().id());
        m.setMinutosTarde(tipo == TipoMovimiento.RETORNO ? v.minutosTarde() : null);
        m = movimientos.save(m);
        auditoria.registrar(m.getRegistradoPor(), "MOVIMIENTO_" + tipo.name(), "movimiento_garita", m.getId(),
                "residente=" + m.getResidenteId() + (m.getMinutosTarde() != null && m.getMinutosTarde() > 0 ? " tarde=" + m.getMinutosTarde() + "min" : ""));
        return aResponse(m);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MovimientoResponse> historial(Pageable pageable) {
        return PaginaResponse.de(movimientos.findAllByOrderByRegistradoEnDesc(pageable), this::aResponse);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MovimientoResponse> mios(Pageable pageable) {
        return PaginaResponse.de(movimientos.findByResidenteIdOrderByRegistradoEnDesc(residenteService.residenteActual().getId(), pageable), this::aResponse);
    }

    /** Corrección auditada: sólo puede anularse el último movimiento vigente del residente. */
    @Transactional
    public MovimientoResponse anular(Long id, String motivo) {
        var m = movimientos.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Movimiento no encontrado"));
        residentes.findByIdForUpdate(m.getResidenteId());
        if (m.isAnulado()) {
            throw new Excepciones.Conflicto("El movimiento ya está anulado");
        }
        var ultimo = movimientos.findFirstByResidenteIdAndAnuladoFalseOrderByRegistradoEnDescIdDesc(m.getResidenteId()).orElseThrow();
        if (!ultimo.getId().equals(m.getId())) {
            throw new Excepciones.Conflicto("Sólo se puede anular el último movimiento registrado del residente");
        }
        m.setAnulado(true);
        m.setAnuladoPor(sesion.obtener().id());
        m.setMotivoAnulacion(motivo);
        auditoria.registrar(m.getAnuladoPor(), "MOVIMIENTO_ANULADO", "movimiento_garita", id, motivo);
        return aResponse(m);
    }

    // ------------------------------------------------------------------ evaluación

    private record Evaluacion(VerificacionResponse respuesta, Residente residente) {
    }

    private Evaluacion evaluar(String contenidoQr) {
        var resolucion = qr.resolver(contenidoQr);
        switch (resolucion.estado()) {
            case INVALIDO -> {
                return negado(Resultado.QR_INVALIDO, "El código QR no es válido", null);
            }
            case REVOCADO -> {
                return negado(Resultado.QR_REVOCADO, "El código QR fue revocado; el residente debe usar su QR vigente", null);
            }
            default -> { }
        }
        Residente residente = resolucion.residente();
        if (residente == null || !residente.isActivo()) {
            return negado(Resultado.RESIDENTE_INACTIVO, "El residente no está activo", residente);
        }
        Instant ahora = reloj.instant();
        var ultimo = movimientos.findFirstByResidenteIdAndAnuladoFalseOrderByRegistradoEnDescIdDesc(residente.getId());

        // Salida abierta: el siguiente movimiento válido es el retorno (siempre se registra, aun fuera de horario).
        if (ultimo.isPresent() && ultimo.get().getTipo() == TipoMovimiento.SALIDA) {
            Long solicitudId = ultimo.get().getSolicitudId();
            var permiso = solicitudes.findById(solicitudId).orElseThrow();
            long tarde = Math.max(0, Duration.between(permiso.getRetornoAutorizado(), ahora).toMinutes());
            Resultado r = tarde > 0 ? Resultado.RETORNO_FUERA_DE_HORARIO : Resultado.AUTORIZADO_RETORNO;
            String msg = tarde > 0 ? "Retorno registrable con " + tarde + " min de retraso; requiere revisión del preceptor" : "Retorno dentro del horario autorizado";
            return new Evaluacion(new VerificacionResponse(r, msg, datos(residente), TipoMovimiento.RETORNO, solicitudId,
                    permiso.getRetornoAutorizado(), (int) tarde), residente);
        }

        List<SolicitudPermiso> aprobados = solicitudes.findAprobadasNoVencidas(residente.getId(), Instant.EPOCH).stream()
                .filter(s -> !movimientos.existsBySolicitudIdAndTipoAndAnuladoFalse(s.getId(), TipoMovimiento.SALIDA))
                .sorted(Comparator.comparing(SolicitudPermiso::getSalidaSolicitada)).toList();
        var vigente = aprobados.stream()
                .filter(s -> !s.getSalidaSolicitada().isAfter(ahora) && !s.getRetornoAutorizado().isBefore(ahora)).findFirst();
        if (vigente.isPresent()) {
            var s = vigente.get();
            return new Evaluacion(new VerificacionResponse(Resultado.AUTORIZADO_SALIDA, "Permiso vigente hasta el retorno autorizado", datos(residente),
                    TipoMovimiento.SALIDA, s.getId(), s.getRetornoAutorizado(), null), residente);
        }
        if (aprobados.stream().anyMatch(s -> s.getSalidaSolicitada().isAfter(ahora))) {
            return negado(Resultado.PERMISO_AUN_NO_VIGENTE, "El permiso aprobado todavía no está en su horario de salida", residente);
        }
        if (!aprobados.isEmpty()) {
            return negado(Resultado.PERMISO_VENCIDO, "El permiso aprobado ya venció", residente);
        }
        return negado(Resultado.SIN_PERMISO, "El residente no tiene un permiso aprobado para salir", residente);
    }

    private Evaluacion negado(Resultado resultado, String mensaje, Residente residente) {
        return new Evaluacion(new VerificacionResponse(resultado, mensaje, residente == null ? null : datos(residente), null, null, null, null), residente);
    }

    private ResidenteGarita datos(Residente r) {
        String nombre = usuarios.findById(r.getUsuarioId()).orElseThrow().getNombreCompleto();
        String habitacion = asignaciones.findByResidenteIdAndHastaIsNull(r.getId())
                .flatMap(a -> habitaciones.findById(a.getHabitacionId())).map(h -> h.getNumero()).orElse(null);
        return new ResidenteGarita(r.getId(), r.getCodigo(), nombre, habitacion);
    }

    private MovimientoResponse aResponse(MovimientoGarita m) {
        var r = residentes.findById(m.getResidenteId()).orElseThrow();
        String nombre = usuarios.findById(r.getUsuarioId()).orElseThrow().getNombreCompleto();
        return new MovimientoResponse(m.getId(), r.getId(), r.getCodigo(), nombre, m.getSolicitudId(), m.getTipo(), m.getRegistradoEn(),
                m.getMinutosTarde(), m.isAnulado(), m.getMotivoAnulacion());
    }
}
