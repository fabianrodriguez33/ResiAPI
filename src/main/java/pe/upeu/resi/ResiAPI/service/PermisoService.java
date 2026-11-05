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
import pe.upeu.resi.ResiAPI.entity.Apoderado;
import pe.upeu.resi.ResiAPI.entity.ResidenteApoderado;
import pe.upeu.resi.ResiAPI.enums.OrigenSolicitud;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.HijoResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.ResumenResidenteResponse;
import pe.upeu.resi.ResiAPI.repository.SesionCultoRepository;
import pe.upeu.resi.ResiAPI.repository.AsistenciaCultoRepository;
import pe.upeu.resi.ResiAPI.repository.EvaluacionLimpiezaRepository;
import java.time.LocalDate;
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
    private final SesionCultoRepository sesiones;
    private final AsistenciaCultoRepository asistenciasRepo;
    private final EvaluacionLimpiezaRepository evaluaciones;
    private final ResidenteApoderadoRepository vinculos;
    private final ApoderadoRepository apoderados;
    private final UsuarioRepository usuarios;
    private final ResidenteService residenteService;
    private final EvidenciaService evidenciaService;
    private final ConfiguracionService config;
    private final AuditoriaService auditoria;
    private final NotificacionService avisos;
    private final HabilitacionService habilitacion;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    // ------------------------------------------------------------------ solicitud

    /** Solicitud enviada por el propio residente: solo corto y largo. Sin el privilegio, pasa primero por su apoderado. */
    @Transactional
    public SolicitudResponse crear(SolicitudRequest r) {
        Residente residente = residenteService.residenteActual();
        habilitacion.exigirHabilitado(residente, true);
        if (r.tipo() == TipoPermiso.PERNOCTE) {
            throw new Excepciones.Prohibido("Los permisos para pernoctar fuera los envía tu padre, madre o apoderado desde su cuenta");
        }
        boolean requiereApoderado = !residente.isPuedeSolicitarSolo() && config.booleano("permiso.apoderado.requerido", true);
        if (requiereApoderado && vinculos.findByResidenteId(residente.getId()).isEmpty()) {
            throw new Excepciones.Conflicto("No tienes un apoderado registrado; comunícate con administración");
        }
        return guardar(residente, r, OrigenSolicitud.RESIDENTE, requiereApoderado);
    }

    /** Solicitud enviada por el padre/madre/apoderado de un residente (cualquier tipo): va directo al pastor. */
    @Transactional
    public SolicitudResponse crearComoApoderado(SolicitudRequest r) {
        if (r.residenteId() == null) {
            throw new Excepciones.SolicitudInvalida("Indique el hijo", Map.of("residenteId", "Indique para quién es el permiso"));
        }
        var residente = residentes.findById(exigirHijo(r.residenteId())).orElseThrow();
        habilitacion.exigirHabilitado(residente, false);
        return guardar(residente, r, OrigenSolicitud.APODERADO, false);
    }

    private SolicitudResponse guardar(Residente residente, SolicitudRequest r, OrigenSolicitud origen, boolean requiereApoderado) {
        if (r.claveCliente() != null && !r.claveCliente().isBlank()) {
            var previa = solicitudes.findByCreadoPorAndClaveCliente(sesion.obtener().id(), r.claveCliente().trim());
            if (previa.isPresent()) {
                return aResponse(previa.get(), false); // el envío se repitió: ya estaba creada
            }
        }
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
        if (r.tipo() == TipoPermiso.PERNOCTE) {
            if (r.responsableNombre() == null || r.responsableNombre().isBlank()) {
                errores.put("responsableNombre", "Indique el nombre del responsable de la casa a donde va");
            }
            if (r.responsableCelular() == null || !r.responsableCelular().trim().matches("[+0-9 ()-]{6,30}")) {
                errores.put("responsableCelular", "Indique un celular válido del responsable");
            }
            if (r.retorno().isAfter(r.salida())
                    && !r.retorno().atZone(zona).toLocalDate().isAfter(r.salida().atZone(zona).toLocalDate())) {
                errores.put("retorno", "Pernoctar fuera implica volver otro día");
            }
        }
        if (!errores.isEmpty()) {
            throw new Excepciones.SolicitudInvalida("Datos inválidos", errores);
        }
        validarReglasConfigurables(r, ahora);
        if (solicitudes.existeSolapada(residente.getId(), r.salida(), r.retorno())) {
            throw new Excepciones.Conflicto("Ya hay un permiso pendiente o aprobado que se superpone con ese horario");
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
        s.setOrigen(origen);
        s.setCreadoPor(sesion.obtener().id());
        if (r.claveCliente() != null && !r.claveCliente().isBlank()) {
            s.setClaveCliente(r.claveCliente().trim());
        }
        if (r.tipo() == TipoPermiso.PERNOCTE) {
            s.setResponsableNombre(r.responsableNombre().trim());
            s.setResponsableCelular(r.responsableCelular().trim());
            s.setAcompanante(r.acompanante() == null || r.acompanante().isBlank() ? "Nadie" : r.acompanante().trim());
        }
        s.setEstado(requiereApoderado ? EstadoSolicitud.PENDIENTE_APODERADO : EstadoSolicitud.APROBADO);
        if (!requiereApoderado) {
            // Lo envía el padre/madre, o el residente con el privilegio firmado: no hay nadie más que lo apruebe.
            s.setDecididoPor(sesion.obtener().id());
            s.setDecididoEn(ahora);
        }
        s.setCreadoEn(ahora);
        s.setActualizadoEn(ahora);
        s = solicitudes.save(s);
        auditoria.registrar(sesion.obtener().id(), "PERMISO_SOLICITADO", "solicitud_permiso", s.getId(), r.tipo().name() + " por " + origen);
        avisarSolicitud(s, residente.getId());
        return aResponse(s, false);
    }

    private static final java.time.format.DateTimeFormatter HORA = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private String cuando(Instant instante) {
        return HORA.format(instante.atZone(zona));
    }

    private void avisarSolicitud(SolicitudPermiso s, Long residenteId) {
        String nombre = avisos.nombreDe(residenteId);
        String detalle = " (" + cuando(s.getSalidaSolicitada()) + " a " + cuando(s.getRetornoSolicitado()) + ")";
        if (s.getEstado() == EstadoSolicitud.PENDIENTE_APODERADO) {
            avisos.aApoderadosDe(residenteId, "Permiso por aprobar", nombre + " pidió un permiso " + s.getTipo().name().toLowerCase() + detalle + ". Apruébalo o recházalo en la app.");
        } else {
            if (s.getOrigen() == OrigenSolicitud.APODERADO) {
                avisos.alResidente(residenteId, "Permiso aprobado", "Tu familia te dejó un permiso " + s.getTipo().name().toLowerCase() + detalle + ".");
            }
            avisos.aRol(Rol.PRECEPTOR, "Nuevo permiso aprobado", nombre + ": permiso " + s.getTipo().name().toLowerCase() + detalle + ".");
        }
    }

    private void validarReglasConfigurables(SolicitudRequest r, Instant ahora) {
        long minutos = Duration.between(r.salida(), r.retorno()).toMinutes();
        Long max = switch (r.tipo()) {
            case CORTO -> config.numero("permiso.corto.max_horas").orElse(null);
            case LARGO -> config.numero("permiso.largo.max_horas").orElse(null);
            case PERNOCTE -> null;
        };
        if (max != null && minutos > max * 60) {
            String mensaje = r.tipo() == TipoPermiso.CORTO
                    ? "Un permiso corto dura como máximo " + max + " horas; si necesitas más tiempo solicita un permiso largo"
                    : "El permiso excede la duración máxima de " + max + " horas";
            throw new Excepciones.SolicitudInvalida(mensaje, Map.of("retorno", r.tipo() == TipoPermiso.CORTO
                    ? "Máximo " + max + " horas: pide un permiso largo" : "Excede las " + max + " horas permitidas"));
        }
        var anticipacion = config.numero("permiso.anticipacion_horas");
        if (anticipacion.isPresent()) {
            DayOfWeek dia = r.salida().atZone(zona).getDayOfWeek();
            boolean finDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
            if ((finDeSemana || r.tipo() == TipoPermiso.PERNOCTE) && Duration.between(ahora, r.salida()).toHours() < anticipacion.get()) {
                throw new Excepciones.SolicitudInvalida("Este permiso requiere " + anticipacion.get() + " horas de anticipación",
                        Map.of("salida", "Requiere " + anticipacion.get() + " horas de anticipación"));
            }
        }
    }

    // ------------------------------------------------------------------ cuenta del apoderado

    private Apoderado apoderadoActual() {
        return apoderados.findByUsuarioId(sesion.obtener().id()).orElseThrow(() -> new Excepciones.Prohibido("El usuario no es un apoderado"));
    }

    private List<Long> hijosIds() {
        return vinculos.findByApoderadoId(apoderadoActual().getId()).stream().map(ResidenteApoderado::getResidenteId).toList();
    }

    /** Falla con «no encontrado» si el residente no es hijo del apoderado de la sesión (no revela su existencia). */
    private Long exigirHijo(Long residenteId) {
        if (!hijosIds().contains(residenteId)) {
            throw new Excepciones.RecursoNoEncontrado("Residente no encontrado");
        }
        return residenteId;
    }

    @Transactional(readOnly = true)
    public List<HijoResponse> hijos() {
        var ids = hijosIds();
        return residentes.findAllById(ids).stream().filter(Residente::isActivo).map(h -> {
            var datos = residenteService.obtener(h.getId());
            var hab = datos.habitacion();
            return new HijoResponse(h.getId(), h.getCodigo(), datos.nombreCompleto(), hab == null ? null : hab.numero(), hab == null ? null : hab.cama(),
                    h.isPuedeSolicitarSolo(), solicitudes.countByResidenteIdInAndEstado(List.of(h.getId()), EstadoSolicitud.PENDIENTE_APODERADO));
        }).sorted(java.util.Comparator.comparing(HijoResponse::nombreCompleto)).toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SolicitudResponse> deMisHijos(EstadoSolicitud estado, Pageable pageable) {
        var ids = hijosIds();
        if (ids.isEmpty()) {
            return PaginaResponse.de(org.springframework.data.domain.Page.<SolicitudPermiso>empty(pageable), s -> aResponse(s, false));
        }
        var pagina = estado == null ? solicitudes.findByResidenteIdInOrderByCreadoEnDesc(ids, pageable)
                : solicitudes.findByResidenteIdInAndEstadoOrderByCreadoEnDesc(ids, estado, pageable);
        return PaginaResponse.de(pagina, s -> aResponse(s, false));
    }

    @Transactional(readOnly = true)
    public SolicitudResponse detalleDeHijo(Long id) {
        var s = solicitudes.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        exigirHijo(s.getResidenteId());
        return aResponse(s, true);
    }

    /** El apoderado acepta o rechaza el permiso que su hijo pidió; si acepta, pasa al pastor. */
    @Transactional
    public SolicitudResponse decidirComoApoderado(Long id, boolean aprobar) {
        var s = solicitudes.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        exigirHijo(s.getResidenteId());
        if (s.getEstado() != EstadoSolicitud.PENDIENTE_APODERADO) {
            throw new Excepciones.Conflicto("Esta solicitud ya no está pendiente de tu decisión");
        }
        resolverPorFamilia(s, aprobar, sesion.obtener().id());
        auditoria.registrar(sesion.obtener().id(), aprobar ? "APODERADO_APROBO" : "APODERADO_RECHAZO", "solicitud_permiso", id, null);
        return aResponse(s, false);
    }

    @Transactional
    public SolicitudResponse cancelarComoApoderado(Long id) {
        var s = solicitudes.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        exigirHijo(s.getResidenteId());
        return cancelarSolicitud(s);
    }

    // ------------------------------------------------------------------ resumen para el pastor

    /** Asistencia a cultos y limpieza de los últimos 30 días del residente de la solicitud (informativo, no aprueba nada). */
    @Transactional(readOnly = true)
    public ResumenResidenteResponse resumenDelResidente(Long solicitudId) {
        var s = solicitudes.findById(solicitudId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        int dias = 30;
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        LocalDate desde = hoy.minusDays(dias);
        long cultos = sesiones.countByFechaBetween(desde, hoy);
        long asistencias = asistenciasRepo.contarEnPeriodo(s.getResidenteId(), desde, hoy);
        long tardanzas = asistenciasRepo.contarTardanzasEnPeriodo(s.getResidenteId(), desde, hoy);
        var evaluadas = evaluaciones.findByResidenteIdOrderByFechaDesc(s.getResidenteId(), org.springframework.data.domain.PageRequest.of(0, 100)).getContent().stream()
                .filter(e -> !e.getFecha().isBefore(desde)).toList();
        Double promedio = evaluadas.isEmpty() ? null : Math.round(evaluadas.stream().mapToInt(e -> e.getTotal()).average().orElse(0) * 10) / 10.0;
        return new ResumenResidenteResponse(dias, cultos, asistencias, tardanzas, promedio, evaluadas.size());
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SolicitudResponse> mias(Pageable pageable) {
        return PaginaResponse.de(solicitudes.findByResidenteIdOrderByCreadoEnDesc(residenteService.residenteActual().getId(), pageable), s -> aResponse(s, false));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SolicitudResponse> listar(EstadoSolicitud estado, Pageable pageable) {
        var pagina = estado == null ? solicitudes.findAllByOrderByCreadoEnDesc(pageable) : solicitudes.findByEstadoOrderByCreadoEnDesc(estado, pageable);
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
        return cancelarSolicitud(bloquearPropia(id));
    }

    private SolicitudResponse cancelarSolicitud(SolicitudPermiso s) {
        boolean salio = movimientos.existsBySolicitudIdAndTipoAndAnuladoFalse(s.getId(), TipoMovimiento.SALIDA);
        boolean cancelable = s.getEstado() == EstadoSolicitud.PENDIENTE_APODERADO || s.getEstado() == EstadoSolicitud.PENDIENTE_PRECEPTOR
                || (s.getEstado() == EstadoSolicitud.APROBADO && !salio);
        if (!cancelable) {
            throw new Excepciones.Conflicto("La solicitud no se puede cancelar en su estado actual");
        }
        cambiarEstado(s, EstadoSolicitud.CANCELADO);
        auditoria.registrar(sesion.obtener().id(), "PERMISO_CANCELADO", "solicitud_permiso", s.getId(), null);
        return aResponse(s, false);
    }

    /**
     * El pastor anula un permiso aprobado que todavía no se usó: desde ese momento garita ya no deja salir con él.
     * Si el residente ya salió no se anula; su retorno se registra normalmente.
     */
    @Transactional
    public SolicitudResponse anular(Long id, String motivo) {
        var s = solicitudes.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Solicitud no encontrada"));
        if (s.getEstado() != EstadoSolicitud.APROBADO) {
            throw new Excepciones.Conflicto("Solo se puede anular un permiso aprobado");
        }
        if (movimientos.existsBySolicitudIdAndTipoAndAnuladoFalse(id, TipoMovimiento.SALIDA)) {
            throw new Excepciones.Conflicto("El residente ya salió con este permiso");
        }
        cambiarEstado(s, EstadoSolicitud.ANULADO);
        s.setComentarioDecision(motivo.trim());
        s.setDecididoPor(sesion.obtener().id());
        s.setDecididoEn(reloj.instant());
        auditoria.registrar(sesion.obtener().id(), "PERMISO_ANULADO", "solicitud_permiso", id, motivo.trim());
        String detalle = "El pastor anuló el permiso de " + cuando(s.getSalidaSolicitada()) + ". Motivo: " + motivo.trim();
        avisos.alResidente(s.getResidenteId(), "Permiso anulado", detalle);
        avisos.aApoderadosDe(s.getResidenteId(), "Permiso anulado", avisos.nombreDe(s.getResidenteId()) + ": " + detalle);
        return aResponse(s, true);
    }

    /**
     * La aceptación del padre/madre (o del apoderado con enlace) deja el permiso aprobado: el pastor solo consulta la lista.
     * No se aprueba un permiso cuya hora de salida ya pasó.
     */
    private void resolverPorFamilia(SolicitudPermiso s, boolean aprobar, Long usuarioId) {
        Instant ahora = reloj.instant();
        if (aprobar && habilitacion.suspendidoParaSalida(residentes.findById(s.getResidenteId()).orElseThrow(), s.getSalidaSolicitada())) {
            throw new Excepciones.Conflicto("El residente está suspendido esta semana: la solicitud no se puede aprobar");
        }
        if (aprobar && !s.getSalidaSolicitada().isAfter(ahora)) {
            throw new Excepciones.Conflicto("El permiso venció: la hora de salida solicitada ya pasó");
        }
        s.setEstado(aprobar ? EstadoSolicitud.APROBADO : EstadoSolicitud.RECHAZADO);
        s.setDecididoPor(usuarioId);
        s.setDecididoEn(ahora);
        s.setActualizadoEn(ahora);
        String detalle = " (" + cuando(s.getSalidaSolicitada()) + " a " + cuando(s.getRetornoSolicitado()) + ")";
        avisos.alResidente(s.getResidenteId(), aprobar ? "Permiso aprobado" : "Permiso rechazado",
                aprobar ? "Tu familia aprobó tu permiso" + detalle + "." : "Tu familia no aprobó tu permiso" + detalle + ".");
        if (aprobar) {
            avisos.aRol(Rol.PRECEPTOR, "Nuevo permiso aprobado", avisos.nombreDe(s.getResidenteId()) + ": permiso " + s.getTipo().name().toLowerCase() + detalle + ".");
        }
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
        resolverPorFamilia(s, aprobar, null);
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
        avisos.aRol(Rol.PRECEPTOR, "Ampliación por revisar", avisos.nombreDe(s.getResidenteId()) + " pide volver a las " + cuando(a.getRetornoNuevo()) + ". Motivo: " + a.getMotivo());
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
        String hasta = cuando(a.getRetornoNuevo());
        avisos.alResidente(s.getResidenteId(), d.aprobar() ? "Ampliación aprobada" : "Ampliación rechazada",
                d.aprobar() ? "Ahora puedes volver hasta las " + hasta + "." : "No se aprobó volver a las " + hasta + (d.comentario() != null && !d.comentario().isBlank() ? ". " + d.comentario() : "."));
        avisos.aApoderadosDe(s.getResidenteId(), d.aprobar() ? "Ampliación aprobada" : "Ampliación rechazada",
                avisos.nombreDe(s.getResidenteId()) + (d.aprobar() ? " puede volver hasta las " + hasta + "." : ": no se aprobó volver a las " + hasta + "."));
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
        avisos.aRol(Rol.PRECEPTOR, "Justificación por revisar", avisos.nombreDe(s.getResidenteId()) + " justifica su tardanza. Motivo: " + j.getMotivo());
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
        var origen = solicitudes.findById(j.getSolicitudId()).orElseThrow();
        avisos.alResidente(origen.getResidenteId(), d.aprobar() ? "Justificación aceptada" : "Justificación rechazada",
                d.aprobar() ? "El pastor aceptó tu justificación." : "El pastor no aceptó tu justificación" + (d.comentario() != null && !d.comentario().isBlank() ? ": " + d.comentario() : "."));
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

    /**
     * Suspensión por faltas: una solicitud sin aprobar (o por el procedimiento excepcional) de un residente inhabilitado, con salida
     * dentro de la semana de suspensión, se cancela sola. Los permisos ya aprobados no se tocan: los revisa el pastor.
     */
    @Scheduled(fixedDelayString = "PT30M")
    @Transactional
    public int cancelarPendientesDeSuspendidos() {
        int cancelados = 0;
        for (var s : solicitudes.findByEstado(EstadoSolicitud.PENDIENTE_APODERADO)) {
            var residente = residentes.findById(s.getResidenteId()).orElseThrow();
            if (habilitacion.suspendidoParaSalida(residente, s.getSalidaSolicitada())) {
                cambiarEstado(s, EstadoSolicitud.CANCELADO);
                s.setComentarioDecision("Cancelado por suspensión: el residente está inhabilitado esta semana");
                auditoria.registrar(null, "PERMISO_CANCELADO_POR_SUSPENSION", "solicitud_permiso", s.getId(), null);
                avisos.alResidente(residente.getId(), "Permiso cancelado por suspensión", "Tu solicitud para el " + cuando(s.getSalidaSolicitada()) + " se canceló: estás inhabilitado esta semana.");
                avisos.aApoderadosDe(residente.getId(), "Permiso cancelado por suspensión", avisos.nombreDe(residente.getId()) + " está inhabilitado esta semana: su solicitud para el " + cuando(s.getSalidaSolicitada()) + " se canceló.");
                cancelados++;
            }
        }
        return cancelados;
    }

    /** Permisos aprobados y sin usar de residentes inhabilitados esta semana, para que el pastor decida si los anula. */
    @Transactional(readOnly = true)
    public List<SolicitudResponse> deResidentesSuspendidos() {
        return solicitudes.findByEstado(EstadoSolicitud.APROBADO).stream()
                .filter(s -> !movimientos.existsBySolicitudIdAndTipoAndAnuladoFalse(s.getId(), TipoMovimiento.SALIDA))
                .filter(s -> habilitacion.suspendidoParaSalida(residentes.findById(s.getResidenteId()).orElseThrow(), s.getSalidaSolicitada()))
                .map(s -> aResponse(s, false)).toList();
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
        if (actual.rol() == Rol.APODERADO) {
            exigirHijo(s.getResidenteId());
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
                s.getDecididoEn(), s.getComentarioDecision(), s.getCreadoEn(), amp, jus,
                s.getOrigen(), s.getResponsableNombre(), s.getResponsableCelular(), s.getAcompanante());
    }

    private AmpliacionResponse aResponse(AmpliacionPermiso a) {
        var quien = quienEs(a.getSolicitudId());
        return new AmpliacionResponse(a.getId(), a.getSolicitudId(), a.getMotivo(), a.getEvidenciaId(), a.getRetornoOriginal(), a.getRetornoNuevo(),
                a.getEstado(), a.getRevisadaEn(), a.getComentario(), a.getCreadoEn(), quien[0], quien[1]);
    }

    /** Nombre y código del residente dueño de la solicitud, para que el pastor sepa de quién es cada trámite. */
    private String[] quienEs(Long solicitudId) {
        var s = solicitudes.findById(solicitudId).orElseThrow();
        var r = residentes.findById(s.getResidenteId()).orElseThrow();
        return new String[] {usuarios.findById(r.getUsuarioId()).orElseThrow().getNombreCompleto(), r.getCodigo()};
    }

    private JustificacionResponse aResponse(JustificacionPosterior j) {
        var quien = quienEs(j.getSolicitudId());
        return new JustificacionResponse(j.getId(), j.getSolicitudId(), j.getMotivo(), j.getEvidenciaId(), j.getEstado(), j.getRevisadaEn(),
                j.getComentario(), j.getCreadoEn(), quien[0], quien[1]);
    }
}
