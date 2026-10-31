package pe.upeu.resi.ResiAPI.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.AmpliacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.DecisionRequest;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.JustificacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.SolicitudRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.AmpliacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.EnlaceApoderadoResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.JustificacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.SolicitudApoderadoResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.SolicitudResponse;
import pe.upeu.resi.ResiAPI.entity.AmpliacionPermiso;
import pe.upeu.resi.ResiAPI.entity.AprobacionApoderado;
import pe.upeu.resi.ResiAPI.entity.JustificacionPosterior;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.entity.SolicitudPermiso;
import pe.upeu.resi.ResiAPI.enums.DecisionApoderado;
import pe.upeu.resi.ResiAPI.enums.EstadoRevision;
import pe.upeu.resi.ResiAPI.enums.EstadoSolicitud;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.enums.TipoMovimiento;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AmpliacionPermisoRepository;
import pe.upeu.resi.ResiAPI.repository.ApoderadoRepository;
import pe.upeu.resi.ResiAPI.repository.AprobacionApoderadoRepository;
import pe.upeu.resi.ResiAPI.repository.JustificacionPosteriorRepository;
import pe.upeu.resi.ResiAPI.repository.MovimientoGaritaRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteApoderadoRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.SolicitudPermisoRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/**
 * Solicitudes de permiso de salida. Los límites de duración, la anticipación y la exigencia de apoderado/evidencia
 * son configurables y varios están pendientes de confirmación institucional (ver tabla {@code configuracion}).
 */
@Service
@RequiredArgsConstructor
public class PermisoService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SolicitudPermisoRepository solicitudes;
    private final AprobacionApoderadoRepository aprobaciones;
    private final AmpliacionPermisoRepository ampliaciones;
    private final JustificacionPosteriorRepository justificaciones;
    private final MovimientoGaritaRepository movimientos;
    private final ResidenteRepository residentes;
    private final ResidenteApoderadoRepository vinculos;
    private final ApoderadoRepository apoderados;
    private final UsuarioRepository usuarios;
    private final ResidenteService residenteService;
    private final EvidenciaService evidenciaService;
    private final ConfiguracionService config;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    // ------------------------------------------------------------------ solicitud

    @Transactional
    public SolicitudResponse crear(SolicitudRequest r) {
        Residente residente = residenteService.residenteActual();
        if (!residente.isActivo()) {
            throw new Excepciones.Conflicto("El residente no está activo");
        }
        Instant ahora = reloj.instant();
        Map<String, String> errores = new java.util.LinkedHashMap<>();
        if (r.salida().isBefore(ahora.minus(Duration.ofMinutes(5)))) {
            errores.put("salida", "La salida no puede estar en el pasado");
        }
        if (!r.retorno().isAfter(r.salida())) {
            errores.put("retorno", "El retorno debe ser posterior a la salida");
        }
        if (!errores.isEmpty()) {
            throw new Excepciones.SolicitudInvalida("Fechas inválidas", errores);
        }
        validarReglasConfigurables(r, ahora);
        if (solicitudes.existeSolapada(residente.getId(), r.salida(), r.retorno())) {
            throw new Excepciones.Conflicto("Ya tienes un permiso pendiente o aprobado que se superpone con ese horario");
        }
        boolean requiereApoderado = config.booleano("permiso.apoderado.requerido", true);
        if (requiereApoderado && vinculos.findByResidenteId(residente.getId()).isEmpty()) {
            throw new Excepciones.Conflicto("No tienes un apoderado registrado; comunícate con administración");
        }
        var s = new SolicitudPermiso();
        s.setResidenteId(residente.getId());
        s.setTipo(r.tipo());
        s.setMotivo(r.motivo().trim());
        s.setDestino(r.destino().trim());
        s.setSalidaSolicitada(r.salida());
        s.setRetornoSolicitado(r.retorno());
        s.setRetornoAutorizado(r.retorno());
        s.setRequiereApoderado(requiereApoderado);
        s.setEstado(requiereApoderado ? EstadoSolicitud.PENDIENTE_APODERADO : EstadoSolicitud.PENDIENTE_PRECEPTOR);
        s.setCreadoEn(ahora);
        s.setActualizadoEn(ahora);
        s = solicitudes.save(s);
        auditoria.registrar(sesion.obtener().id(), "PERMISO_SOLICITADO", "solicitud_permiso", s.getId(), r.tipo().name());
        return aResponse(s, false);
    }

    private void validarReglasConfigurables(SolicitudRequest r, Instant ahora) {
        long horas = Duration.between(r.salida(), r.retorno()).toMinutes();
        Long max = switch (r.tipo()) {
            case CORTO -> config.numero("permiso.corto.max_horas").orElse(null);
            case LARGO -> config.numero("permiso.largo.max_horas").orElse(null);
            case ESPECIAL -> null;
        };
        if (max != null && horas > max * 60) {
            throw new Excepciones.SolicitudInvalida("El permiso excede la duración máxima de " + max + " horas",
                    Map.of("retorno", "Excede las " + max + " horas permitidas"));
        }
        var anticipacion = config.numero("permiso.anticipacion_horas");
        if (anticipacion.isPresent()) {
            DayOfWeek dia = r.salida().atZone(zona).getDayOfWeek();
            boolean finDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
            if ((finDeSemana || r.tipo() == TipoPermiso.ESPECIAL) && Duration.between(ahora, r.salida()).toHours() < anticipacion.get()) {
                throw new Excepciones.SolicitudInvalida("Este permiso requiere " + anticipacion.get() + " horas de anticipación",
                        Map.of("salida", "Requiere " + anticipacion.get() + " horas de anticipación"));
            }
        }
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SolicitudResponse> mias(Pageable pageable) {
        return PaginaResponse.de(solicitudes.findByResidenteIdOrderByCreadoEnDesc(residenteService.residenteActual().getId(), pageable), s -> aResponse(s, false));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SolicitudResponse> listar(EstadoSolicitud estado, Pageable pageable) {
        var pagina = estado == null ? solicitudes.findAllByOrderByCreadoEnDesc(pageable) : solicitudes.findByEstadoOrderByCreadoEnAsc(estado, pageable);
        return PaginaResponse.de(pagina, s -> aResponse(s, false));
    }

    @Transactional(readOnly = true)
    public SolicitudResponse detalle(Long id) {
        var s = solicitudes.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        verificarAcceso(s);
        return aResponse(s, true);
    }

    @Transactional
    public SolicitudResponse cancelar(Long id) {
        var s = bloquearPropia(id);
        boolean salio = movimientos.existsBySolicitudIdAndTipoAndAnuladoFalse(id, TipoMovimiento.SALIDA);
        boolean cancelable = s.getEstado() == EstadoSolicitud.PENDIENTE_APODERADO || s.getEstado() == EstadoSolicitud.PENDIENTE_PRECEPTOR
                || (s.getEstado() == EstadoSolicitud.APROBADO && !salio);
        if (!cancelable) {
            throw new Excepciones.Conflicto("La solicitud no se puede cancelar en su estado actual");
        }
        cambiarEstado(s, EstadoSolicitud.CANCELADO);
        auditoria.registrar(sesion.obtener().id(), "PERMISO_CANCELADO", "solicitud_permiso", id, null);
        return aResponse(s, false);
    }

    // ------------------------------------------------------------------ decisión del preceptor

    @Transactional
    public SolicitudResponse decidir(Long id, DecisionRequest d) {
        var s = solicitudes.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        if (s.getEstado() != EstadoSolicitud.PENDIENTE_PRECEPTOR) {
            throw new Excepciones.Conflicto("La solicitud no está pendiente de revisión del preceptor (estado: " + s.getEstado() + ")");
        }
        Instant ahora = reloj.instant();
        if (!s.getSalidaSolicitada().isAfter(ahora) && d.aprobar()) {
            cambiarEstado(s, EstadoSolicitud.VENCIDO);
            throw new Excepciones.Conflicto("La solicitud venció: la hora de salida solicitada ya pasó");
        }
        s.setEstado(d.aprobar() ? EstadoSolicitud.APROBADO : EstadoSolicitud.RECHAZADO);
        s.setDecididoPor(sesion.obtener().id());
        s.setDecididoEn(ahora);
        s.setComentarioDecision(d.comentario());
        s.setActualizadoEn(ahora);
        auditoria.registrar(s.getDecididoPor(), d.aprobar() ? "PERMISO_APROBADO" : "PERMISO_RECHAZADO", "solicitud_permiso", id, d.comentario());
        return aResponse(s, false);
    }

    // ------------------------------------------------------------------ apoderado

    /** Genera un enlace de un solo uso para el apoderado. Invalida los enlaces anteriores sin usar. */
    @Transactional
    public EnlaceApoderadoResponse generarEnlace(Long solicitudId) {
        var s = solicitudes.findByIdForUpdate(solicitudId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        if (s.getEstado() != EstadoSolicitud.PENDIENTE_APODERADO) {
            throw new Excepciones.Conflicto("La solicitud no está pendiente de aprobación del apoderado");
        }
        var vinculo = vinculos.findByResidenteId(s.getResidenteId()).stream()
                .sorted((a, b) -> Boolean.compare(b.isPrincipal(), a.isPrincipal())).findFirst()
                .orElseThrow(() -> new Excepciones.Conflicto("El residente no tiene apoderado registrado"));
        var apoderado = apoderados.findById(vinculo.getApoderadoId()).orElseThrow();
        aprobaciones.deleteAll(aprobaciones.findBySolicitudIdAndUsadoEnIsNull(solicitudId));
        aprobaciones.flush();

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant ahora = reloj.instant();
        long horas = config.numero("permiso.apoderado.vigencia_horas").orElse(48L);
        Instant expira = ahora.plus(Duration.ofHours(horas));
        if (expira.isAfter(s.getSalidaSolicitada())) {
            expira = s.getSalidaSolicitada();
        }
        if (!expira.isAfter(ahora)) {
            throw new Excepciones.Conflicto("La hora de salida solicitada ya pasó; no se puede generar el enlace");
        }
        var a = new AprobacionApoderado();
        a.setSolicitudId(solicitudId);
        a.setApoderadoId(apoderado.getId());
        a.setTokenHash(hash(token));
        a.setExpiraEn(expira);
        a.setCreadoEn(ahora);
        aprobaciones.save(a);
        auditoria.registrar(sesion.obtener().id(), "ENLACE_APODERADO_GENERADO", "solicitud_permiso", solicitudId, null);
        return new EnlaceApoderadoResponse(token, "/publico/aprobacion/" + token, expira, apoderado.getNombre());
    }

    @Transactional(readOnly = true)
    public SolicitudApoderadoResponse consultarParaApoderado(String token) {
        var a = aprobacionVigente(token);
        var s = solicitudes.findById(a.getSolicitudId()).orElseThrow();
        var nombre = usuarios.findById(residentes.findById(s.getResidenteId()).orElseThrow().getUsuarioId()).orElseThrow().getNombreCompleto();
        return new SolicitudApoderadoResponse(nombre, s.getTipo().name(), s.getMotivo(), s.getDestino(), s.getSalidaSolicitada(), s.getRetornoSolicitado(), a.getExpiraEn());
    }

    @Transactional
    public void decidirApoderado(String token, boolean aprobar) {
        var a = aprobacionVigente(token);
        var s = solicitudes.findByIdForUpdate(a.getSolicitudId()).orElseThrow();
        if (s.getEstado() != EstadoSolicitud.PENDIENTE_APODERADO) {
            throw new Excepciones.Conflicto("Esta solicitud ya no está pendiente de su decisión");
        }
        Instant ahora = reloj.instant();
        a.setUsadoEn(ahora);
        a.setDecision(aprobar ? DecisionApoderado.APROBADO : DecisionApoderado.RECHAZADO);
        // La aprobación del apoderado no reemplaza la autorización institucional: pasa a revisión del preceptor.
        cambiarEstado(s, aprobar ? EstadoSolicitud.PENDIENTE_PRECEPTOR : EstadoSolicitud.RECHAZADO);
        auditoria.registrar(null, aprobar ? "APODERADO_APROBO" : "APODERADO_RECHAZO", "solicitud_permiso", s.getId(), "apoderado=" + a.getApoderadoId());
    }

    private AprobacionApoderado aprobacionVigente(String token) {
        if (token == null || token.length() > 100) {
            throw new Excepciones.RecursoNoEncontrado("Enlace no válido");
        }
        var a = aprobaciones.findByTokenHash(hash(token)).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Enlace no válido"));
        if (a.getUsadoEn() != null) {
            throw new Excepciones.Conflicto("Este enlace ya fue utilizado");
        }
        if (!a.getExpiraEn().isAfter(reloj.instant())) {
            throw new Excepciones.Conflicto("Este enlace expiró");
        }
        return a;
    }

    // ------------------------------------------------------------------ ampliación (antes del vencimiento)

    @Transactional
    public AmpliacionResponse solicitarAmpliacion(Long solicitudId, AmpliacionRequest r) {
        var s = bloquearPropia(solicitudId);
        Instant ahora = reloj.instant();
        if (s.getEstado() != EstadoSolicitud.APROBADO) {
            throw new Excepciones.Conflicto("Sólo se puede ampliar un permiso aprobado");
        }
        if (!ahora.isBefore(s.getRetornoAutorizado())) {
            throw new Excepciones.Conflicto("El permiso ya venció; presenta una justificación posterior");
        }
        if (!r.retornoNuevo().isAfter(s.getRetornoAutorizado())) {
            throw new Excepciones.SolicitudInvalida("El nuevo retorno debe ser posterior al autorizado",
                    Map.of("retornoNuevo", "Debe ser posterior a " + s.getRetornoAutorizado()));
        }
        if (ampliaciones.existsBySolicitudIdAndEstado(solicitudId, EstadoRevision.PENDIENTE)) {
            throw new Excepciones.Conflicto("Ya hay una ampliación pendiente de revisión");
        }
        Long evidenciaId = validarEvidencia(r.evidenciaId(), "ampliacion.evidencia.obligatoria", true);
        var a = new AmpliacionPermiso();
        a.setSolicitudId(solicitudId);
        a.setMotivo(r.motivo().trim());
        a.setEvidenciaId(evidenciaId);
        a.setRetornoOriginal(s.getRetornoAutorizado());
        a.setRetornoNuevo(r.retornoNuevo());
        a.setEstado(EstadoRevision.PENDIENTE);
        a.setCreadoEn(ahora);
        a = ampliaciones.save(a);
        auditoria.registrar(sesion.obtener().id(), "AMPLIACION_SOLICITADA", "ampliacion_permiso", a.getId(), null);
        return aResponse(a);
    }

    @Transactional
    public AmpliacionResponse decidirAmpliacion(Long ampliacionId, DecisionRequest d) {
        var a = ampliaciones.findByIdForUpdate(ampliacionId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Ampliación no encontrada"));
        var s = solicitudes.findByIdForUpdate(a.getSolicitudId()).orElseThrow();
        if (a.getEstado() != EstadoRevision.PENDIENTE) {
            throw new Excepciones.Conflicto("La ampliación ya fue revisada");
        }
        if (d.aprobar() && s.getEstado() != EstadoSolicitud.APROBADO) {
            throw new Excepciones.Conflicto("El permiso ya no está aprobado");
        }
        Instant ahora = reloj.instant();
        a.setEstado(d.aprobar() ? EstadoRevision.APROBADA : EstadoRevision.RECHAZADA);
        a.setRevisadaPor(sesion.obtener().id());
        a.setRevisadaEn(ahora);
        a.setComentario(d.comentario());
        if (d.aprobar()) {
            // Se conserva el retorno solicitado original; sólo cambia el horario autorizado vigente.
            s.setRetornoAutorizado(a.getRetornoNuevo());
            s.setActualizadoEn(ahora);
        }
        auditoria.registrar(a.getRevisadaPor(), d.aprobar() ? "AMPLIACION_APROBADA" : "AMPLIACION_RECHAZADA", "ampliacion_permiso", ampliacionId, d.comentario());
        return aResponse(a);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AmpliacionResponse> ampliacionesPendientes(Pageable pageable) {
        return PaginaResponse.de(ampliaciones.findByEstadoOrderByCreadoEnAsc(EstadoRevision.PENDIENTE, pageable), this::aResponse);
    }

    // ------------------------------------------------------------------ justificación (después del vencimiento)

    /** No modifica el horario autorizado: queda como un registro diferenciado de una ampliación. */
    @Transactional
    public JustificacionResponse justificar(Long solicitudId, JustificacionRequest r) {
        var s = bloquearPropia(solicitudId);
        if (s.getEstado() != EstadoSolicitud.APROBADO) {
            throw new Excepciones.Conflicto("Sólo se puede justificar un permiso que fue aprobado");
        }
        if (reloj.instant().isBefore(s.getRetornoAutorizado())) {
            throw new Excepciones.Conflicto("El permiso aún no venció; si necesitas más tiempo solicita una ampliación");
        }
        if (justificaciones.existsBySolicitudIdAndEstado(solicitudId, EstadoRevision.PENDIENTE)) {
            throw new Excepciones.Conflicto("Ya hay una justificación pendiente de revisión");
        }
        Long evidenciaId = validarEvidencia(r.evidenciaId(), "justificacion.evidencia.obligatoria", false);
        var j = new JustificacionPosterior();
        j.setSolicitudId(solicitudId);
        j.setMotivo(r.motivo().trim());
        j.setEvidenciaId(evidenciaId);
        j.setEstado(EstadoRevision.PENDIENTE);
        j.setCreadoEn(reloj.instant());
        j = justificaciones.save(j);
        auditoria.registrar(sesion.obtener().id(), "JUSTIFICACION_PRESENTADA", "justificacion_posterior", j.getId(), null);
        return aResponse(j);
    }

    @Transactional
    public JustificacionResponse decidirJustificacion(Long id, DecisionRequest d) {
        var j = justificaciones.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Justificación no encontrada"));
        if (j.getEstado() != EstadoRevision.PENDIENTE) {
            throw new Excepciones.Conflicto("La justificación ya fue revisada");
        }
        j.setEstado(d.aprobar() ? EstadoRevision.APROBADA : EstadoRevision.RECHAZADA);
        j.setRevisadaPor(sesion.obtener().id());
        j.setRevisadaEn(reloj.instant());
        j.setComentario(d.comentario());
        auditoria.registrar(j.getRevisadaPor(), d.aprobar() ? "JUSTIFICACION_ACEPTADA" : "JUSTIFICACION_RECHAZADA", "justificacion_posterior", id, d.comentario());
        return aResponse(j);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<JustificacionResponse> justificacionesPendientes(Pageable pageable) {
        return PaginaResponse.de(justificaciones.findByEstadoOrderByCreadoEnAsc(EstadoRevision.PENDIENTE, pageable), this::aResponse);
    }

    // ------------------------------------------------------------------ vencimiento

    /** Las solicitudes que siguen pendientes cuando llega la hora de salida pasan a VENCIDO. */
    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public int vencerPendientes() {
        return solicitudes.marcarVencidas(reloj.instant());
    }

    // ------------------------------------------------------------------ utilidades

    private Long validarEvidencia(Long evidenciaId, String claveConfig, boolean porDefecto) {
        boolean obligatoria = config.booleano(claveConfig, porDefecto);
        if (evidenciaId == null) {
            if (obligatoria) {
                throw new Excepciones.SolicitudInvalida("La evidencia fotográfica es obligatoria", Map.of("evidenciaId", "Adjunta una fotografía"));
            }
            return null;
        }
        evidenciaService.exigirPropia(evidenciaId, sesion.obtener().id());
        return evidenciaId;
    }

    private SolicitudPermiso bloquearPropia(Long id) {
        var s = solicitudes.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        if (!s.getResidenteId().equals(residenteService.residenteActual().getId())) {
            throw new Excepciones.RecursoNoEncontrado("Solicitud no encontrada");
        }
        return s;
    }

    private void verificarAcceso(SolicitudPermiso s) {
        var actual = sesion.obtener();
        if (actual.rol() == Rol.RESIDENTE && !s.getResidenteId().equals(residenteService.residenteActual().getId())) {
            throw new Excepciones.RecursoNoEncontrado("Solicitud no encontrada");
        }
        if (actual.rol() == Rol.GARITA) {
            throw new Excepciones.Prohibido("Sin acceso");
        }
    }

    private void cambiarEstado(SolicitudPermiso s, EstadoSolicitud estado) {
        s.setEstado(estado);
        s.setActualizadoEn(reloj.instant());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private SolicitudResponse aResponse(SolicitudPermiso s, boolean conDetalle) {
        var residente = residentes.findById(s.getResidenteId()).orElseThrow();
        String nombre = usuarios.findById(residente.getUsuarioId()).orElseThrow().getNombreCompleto();
        List<AmpliacionResponse> amp = conDetalle ? ampliaciones.findBySolicitudIdOrderByCreadoEnDesc(s.getId()).stream().map(this::aResponse).toList() : null;
        List<JustificacionResponse> jus = conDetalle ? justificaciones.findBySolicitudIdOrderByCreadoEnDesc(s.getId()).stream().map(this::aResponse).toList() : null;
        return new SolicitudResponse(s.getId(), residente.getId(), residente.getCodigo(), nombre, s.getTipo(), s.getMotivo(), s.getDestino(),
                s.getSalidaSolicitada(), s.getRetornoSolicitado(), s.getRetornoAutorizado(), s.getEstado(), s.isRequiereApoderado(),
                s.getDecididoEn(), s.getComentarioDecision(), s.getCreadoEn(), amp, jus);
    }

    private AmpliacionResponse aResponse(AmpliacionPermiso a) {
        return new AmpliacionResponse(a.getId(), a.getSolicitudId(), a.getMotivo(), a.getEvidenciaId(), a.getRetornoOriginal(), a.getRetornoNuevo(),
                a.getEstado(), a.getRevisadaEn(), a.getComentario(), a.getCreadoEn());
    }

    private JustificacionResponse aResponse(JustificacionPosterior j) {
        return new JustificacionResponse(j.getId(), j.getSolicitudId(), j.getMotivo(), j.getEvidenciaId(), j.getEstado(), j.getRevisadaEn(),
                j.getComentario(), j.getCreadoEn());
    }
}
