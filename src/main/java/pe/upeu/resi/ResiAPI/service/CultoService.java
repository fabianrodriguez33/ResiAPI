package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.response.CultoResponses.AsistenciaResponse;
import pe.upeu.resi.ResiAPI.dto.response.CultoResponses.ResidentePendienteResponse;
import pe.upeu.resi.ResiAPI.dto.response.CultoResponses.SesionCultoResponse;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.entity.AsistenciaCulto;
import pe.upeu.resi.ResiAPI.entity.SesionCulto;
import pe.upeu.resi.ResiAPI.enums.EstadoSesion;
import pe.upeu.resi.ResiAPI.enums.TipoCulto;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsistenciaCultoRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.SesionCultoRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

@Service
@RequiredArgsConstructor
public class CultoService {

    private static final Set<DayOfWeek> DIAS_DE_CULTO = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final SesionCultoRepository sesiones;
    private final AsistenciaCultoRepository asistencias;
    private final ResidenteRepository residentes;
    private final UsuarioRepository usuarios;
    private final QrService qr;
    private final ConfiguracionService config;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;
    private final ResidenteService residenteService;

    /** Abre (o devuelve, si ya existe) la sesión de culto de hoy. Horarios y cortes salen de la configuración. */
    @Transactional
    public SesionCultoResponse abrir(TipoCulto tipo) {
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        if (!DIAS_DE_CULTO.contains(hoy.getDayOfWeek())) {
            throw new Excepciones.Conflicto("Los cultos se registran de lunes a viernes");
        }
        var existente = sesiones.findByFechaAndTipo(hoy, tipo);
        if (existente.isPresent()) {
            return aResponse(existente.get());
        }
        String base = "culto." + tipo.name().toLowerCase();
        var s = new SesionCulto();
        s.setFecha(hoy);
        s.setTipo(tipo);
        s.setInicio(hoy.atTime(config.hora(base + ".inicio")).atZone(zona).toInstant());
        s.setCorte(hoy.atTime(config.hora(base + ".corte")).atZone(zona).toInstant());
        s.setEstado(EstadoSesion.ABIERTA);
        s.setAbiertaPor(sesion.obtener().id());
        s.setCreadoEn(reloj.instant());
        s = sesiones.save(s);
        auditoria.registrar(s.getAbiertaPor(), "CULTO_ABIERTO", "sesion_culto", s.getId(), tipo.name());
        return aResponse(s);
    }

    @Transactional(readOnly = true)
    public List<SesionCultoResponse> deHoy() {
        return sesiones.findByFechaOrderByInicioAsc(reloj.instant().atZone(zona).toLocalDate()).stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public SesionCultoResponse obtener(Long id) {
        return aResponse(buscarSesion(id));
    }

    @Transactional
    public AsistenciaResponse registrar(Long sesionId, String contenidoQr) {
        var s = sesiones.findByIdForUpdate(sesionId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Sesión de culto no encontrada"));
        if (s.getEstado() != EstadoSesion.ABIERTA) {
            throw new Excepciones.Conflicto("La sesión de culto está cerrada");
        }
        var resolucion = qr.resolver(contenidoQr);
        switch (resolucion.estado()) {
            case INVALIDO -> throw new Excepciones.SolicitudInvalida("El código QR no es válido");
            case REVOCADO -> throw new Excepciones.Conflicto("El código QR fue revocado");
            default -> { }
        }
        var residente = resolucion.residente();
        if (residente == null || !residente.isActivo()) {
            throw new Excepciones.Conflicto("El residente no está activo");
        }
        var ahora = reloj.instant();
        var previa = asistencias.findBySesionIdAndResidenteId(sesionId, residente.getId());
        AsistenciaCulto a;
        if (previa.isPresent() && !previa.get().isAnulada()) {
            throw new Excepciones.Conflicto("El residente ya registró su asistencia en este culto");
        } else if (previa.isPresent()) {
            a = previa.get();
            a.setAnulada(false);
            a.setAnuladaPor(null);
            a.setMotivoAnulacion(null);
        } else {
            a = new AsistenciaCulto();
            a.setSesionId(sesionId);
            a.setResidenteId(residente.getId());
        }
        a.setRegistradoEn(ahora);
        a.setRegistradoPor(sesion.obtener().id());
        a.setDespuesDelCorte(ahora.isAfter(s.getCorte()));
        a = asistencias.save(a);
        auditoria.registrar(a.getRegistradoPor(), "ASISTENCIA_REGISTRADA", "asistencia_culto", a.getId(), "residente=" + residente.getId());
        return aResponse(a, s);
    }

    @Transactional(readOnly = true)
    public List<AsistenciaResponse> presentes(Long sesionId) {
        var s = buscarSesion(sesionId);
        return asistencias.findBySesionIdAndAnuladaFalse(sesionId).stream().map(a -> aResponse(a, s)).toList();
    }

    @Transactional(readOnly = true)
    public List<ResidentePendienteResponse> pendientes(Long sesionId) {
        buscarSesion(sesionId);
        Set<Long> presentes = asistencias.findBySesionIdAndAnuladaFalse(sesionId).stream()
                .map(AsistenciaCulto::getResidenteId).collect(Collectors.toSet());
        return residentes.findByActivoTrue().stream().filter(r -> !presentes.contains(r.getId()))
                .map(r -> new ResidentePendienteResponse(r.getId(), r.getCodigo(), usuarios.findById(r.getUsuarioId()).orElseThrow().getNombreCompleto()))
                .sorted((x, y) -> x.codigo().compareTo(y.codigo())).toList();
    }

    @Transactional
    public SesionCultoResponse cerrar(Long sesionId) {
        var s = sesiones.findByIdForUpdate(sesionId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Sesión de culto no encontrada"));
        if (s.getEstado() == EstadoSesion.CERRADA) {
            throw new Excepciones.Conflicto("La sesión ya está cerrada");
        }
        s.setEstado(EstadoSesion.CERRADA);
        s.setCerradaEn(reloj.instant());
        auditoria.registrar(sesion.obtener().id(), "CULTO_CERRADO", "sesion_culto", sesionId, null);
        return aResponse(s);
    }

    /** Corrección auditada: la asistencia se marca como anulada, no se borra. */
    @Transactional
    public void anular(Long asistenciaId, String motivo) {
        var a = asistencias.findById(asistenciaId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Asistencia no encontrada"));
        if (a.isAnulada()) {
            throw new Excepciones.Conflicto("La asistencia ya está anulada");
        }
        a.setAnulada(true);
        a.setAnuladaPor(sesion.obtener().id());
        a.setMotivoAnulacion(motivo);
        auditoria.registrar(a.getAnuladaPor(), "ASISTENCIA_ANULADA", "asistencia_culto", asistenciaId, motivo);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AsistenciaResponse> deResidenteActual(Pageable pageable) {
        var r = residenteService.residenteActual();
        return PaginaResponse.de(asistencias.findByResidenteIdAndAnuladaFalseOrderByRegistradoEnDesc(r.getId(), pageable),
                a -> aResponse(a, buscarSesion(a.getSesionId())));
    }

    private SesionCulto buscarSesion(Long id) {
        return sesiones.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Sesión de culto no encontrada"));
    }

    private SesionCultoResponse aResponse(SesionCulto s) {
        return new SesionCultoResponse(s.getId(), s.getFecha(), s.getTipo(), s.getInicio(), s.getCorte(), s.getEstado(),
                asistencias.findBySesionIdAndAnuladaFalse(s.getId()).size());
    }

    private AsistenciaResponse aResponse(AsistenciaCulto a, SesionCulto s) {
        var res = residentes.findById(a.getResidenteId()).orElseThrow();
        String nombre = usuarios.findById(res.getUsuarioId()).orElseThrow().getNombreCompleto();
        return new AsistenciaResponse(a.getId(), s.getId(), s.getFecha(), s.getTipo(), res.getId(), res.getCodigo(), nombre,
                a.getRegistradoEn(), a.isDespuesDelCorte());
    }
}
