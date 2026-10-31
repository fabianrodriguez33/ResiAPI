package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ApoderadoRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ResidenteActualizarRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ResidenteRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.ApoderadoResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.HabitacionResumen;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.ResidenteResponse;
import pe.upeu.resi.ResiAPI.entity.Apoderado;
import pe.upeu.resi.ResiAPI.entity.AsignacionHabitacion;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.entity.ResidenteApoderado;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.ApoderadoRepository;
import pe.upeu.resi.ResiAPI.repository.AsignacionHabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.HabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteApoderadoRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

@Service
@RequiredArgsConstructor
public class ResidenteService {

    private final ResidenteRepository residentes;
    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;
    private final HabitacionRepository habitaciones;
    private final AsignacionHabitacionRepository asignaciones;
    private final ApoderadoRepository apoderados;
    private final ResidenteApoderadoRepository vinculos;
    private final QrService qr;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;

    @Transactional
    public ResidenteResponse crear(ResidenteRequest r) {
        String codigo = r.codigo().trim();
        if (residentes.existsByCodigo(codigo)) {
            throw new Excepciones.Conflicto("Ya existe un residente con el código " + codigo);
        }
        var usuario = usuarioService.crear(r.username(), r.password(), r.nombreCompleto(), Rol.RESIDENTE);
        var res = new Residente();
        res.setUsuarioId(usuario.getId());
        res.setCodigo(codigo);
        res.setTelefono(r.telefono());
        res.setActivo(true);
        res.setCreadoEn(reloj.instant());
        res = residentes.save(res);
        qr.emitir(res.getId());
        auditoria.registrar(sesion.obtener().id(), "RESIDENTE_CREADO", "residente", res.getId(), "codigo=" + codigo);
        return aResponse(res);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ResidenteResponse> listar(Pageable pageable) {
        return PaginaResponse.de(residentes.findAllByOrderByCodigoAsc(pageable), this::aResponse);
    }

    @Transactional(readOnly = true)
    public ResidenteResponse obtener(Long id) {
        return aResponse(buscar(id));
    }

    @Transactional(readOnly = true)
    public ResidenteResponse yo() {
        return aResponse(residenteActual());
    }

    /** Residente asociado a la sesión; falla si el usuario autenticado no es un residente. */
    public Residente residenteActual() {
        return residentes.findByUsuarioId(sesion.obtener().id())
                .orElseThrow(() -> new Excepciones.Prohibido("El usuario no es un residente"));
    }

    public Residente buscar(Long id) {
        return residentes.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
    }

    @Transactional
    public ResidenteResponse actualizar(Long id, ResidenteActualizarRequest r) {
        var res = residentes.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
        var usuario = usuarios.findById(res.getUsuarioId()).orElseThrow();
        if (r.nombreCompleto() != null && !r.nombreCompleto().isBlank()) {
            usuario.setNombreCompleto(r.nombreCompleto().trim());
        }
        if (r.telefono() != null) {
            res.setTelefono(r.telefono());
        }
        if (r.activo() != null && r.activo() != res.isActivo()) {
            res.setActivo(r.activo());
            usuario.setActivo(r.activo());
            if (!r.activo()) {
                // Se libera el cupo de la habitación conservando el historial de asignaciones.
                asignaciones.findByResidenteIdAndHastaIsNull(id).ifPresent(a -> a.setHasta(reloj.instant()));
            }
            auditoria.registrar(sesion.obtener().id(), r.activo() ? "RESIDENTE_ACTIVADO" : "RESIDENTE_DESACTIVADO", "residente", id, null);
        }
        return aResponse(res);
    }

    @Transactional
    public ResidenteResponse asignarHabitacion(Long residenteId, Long habitacionId) {
        var res = residentes.findByIdForUpdate(residenteId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
        if (!res.isActivo()) {
            throw new Excepciones.Conflicto("No se puede asignar habitación a un residente inactivo");
        }
        // El bloqueo de la habitación serializa asignaciones concurrentes y protege la capacidad máxima.
        var hab = habitaciones.findByIdForUpdate(habitacionId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Habitación no encontrada"));
        if (!hab.isActiva()) {
            throw new Excepciones.Conflicto("La habitación " + hab.getNumero() + " está inactiva");
        }
        var vigente = asignaciones.findByResidenteIdAndHastaIsNull(residenteId);
        if (vigente.isPresent() && vigente.get().getHabitacionId().equals(habitacionId)) {
            throw new Excepciones.Conflicto("El residente ya está asignado a esa habitación");
        }
        if (asignaciones.countByHabitacionIdAndHastaIsNull(habitacionId) >= hab.getCapacidad()) {
            throw new Excepciones.Conflicto("La habitación " + hab.getNumero() + " no tiene cupos disponibles");
        }
        var ahora = reloj.instant();
        vigente.ifPresent(a -> {
            a.setHasta(ahora);
            asignaciones.saveAndFlush(a);
        });
        var nueva = new AsignacionHabitacion();
        nueva.setResidenteId(residenteId);
        nueva.setHabitacionId(habitacionId);
        nueva.setDesde(ahora);
        asignaciones.save(nueva);
        auditoria.registrar(sesion.obtener().id(), "HABITACION_ASIGNADA", "residente", residenteId, "habitacion=" + hab.getNumero());
        return aResponse(res);
    }

    @Transactional
    public ApoderadoResponse agregarApoderado(Long residenteId, ApoderadoRequest r) {
        buscar(residenteId);
        var a = new Apoderado();
        a.setNombre(r.nombre().trim());
        a.setTelefono(r.telefono());
        a.setEmail(r.email());
        a = apoderados.save(a);
        var v = new ResidenteApoderado();
        v.setResidenteId(residenteId);
        v.setApoderadoId(a.getId());
        v.setParentesco(r.parentesco());
        v.setPrincipal(r.principal());
        vinculos.save(v);
        auditoria.registrar(sesion.obtener().id(), "APODERADO_AGREGADO", "residente", residenteId, null);
        return new ApoderadoResponse(a.getId(), a.getNombre(), a.getTelefono(), a.getEmail(), v.getParentesco(), v.isPrincipal());
    }

    @Transactional(readOnly = true)
    public List<ApoderadoResponse> apoderados(Long residenteId) {
        buscar(residenteId);
        return vinculos.findByResidenteId(residenteId).stream().map(v -> {
            var a = apoderados.findById(v.getApoderadoId()).orElseThrow();
            return new ApoderadoResponse(a.getId(), a.getNombre(), a.getTelefono(), a.getEmail(), v.getParentesco(), v.isPrincipal());
        }).toList();
    }

    private ResidenteResponse aResponse(Residente r) {
        var usuario = usuarios.findById(r.getUsuarioId()).orElseThrow();
        HabitacionResumen hab = asignaciones.findByResidenteIdAndHastaIsNull(r.getId())
                .flatMap(a -> habitaciones.findById(a.getHabitacionId()))
                .map(h -> new HabitacionResumen(h.getId(), h.getNumero())).orElse(null);
        return new ResidenteResponse(r.getId(), r.getUsuarioId(), r.getCodigo(), usuario.getNombreCompleto(), r.getTelefono(), r.isActivo(), hab);
    }
}
