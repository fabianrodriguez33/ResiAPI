package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ApoderadoRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.PerfilRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ResidenteActualizarRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ResidenteRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.ApoderadoResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.HabitacionResumen;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.ResidenteResponse;
import pe.upeu.resi.ResiAPI.entity.Apoderado;
import pe.upeu.resi.ResiAPI.entity.AsignacionHabitacion;
import pe.upeu.resi.ResiAPI.entity.Habitacion;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.entity.Usuario;
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

    /** El residente edita sus propios datos de contacto y académicos. El nombre completo lo gestiona la administración. */
    @Transactional
    public ResidenteResponse actualizarPerfil(PerfilRequest r) {
        var res = residentes.findByIdForUpdate(residenteActual().getId()).orElseThrow();
        if (r.codigo() != null && !r.codigo().equals(res.getCodigo())) {
            if (residentes.existsByCodigo(r.codigo())) {
                throw new Excepciones.Conflicto("Ya existe un residente con el código " + r.codigo());
            }
            res.setCodigo(r.codigo());
        }
        if (r.email() != null) {
            res.setEmail(r.email().isBlank() ? null : r.email().trim());
        }
        if (r.telefono() != null) {
            res.setTelefono(r.telefono().isBlank() ? null : r.telefono().trim());
        }
        if (r.carrera() != null) {
            res.setCarrera(r.carrera().isBlank() ? null : r.carrera().trim());
        }
        if (r.ciclo() != null) {
            res.setCiclo(r.ciclo().shortValue());
        }
        auditoria.registrar(sesion.obtener().id(), "PERFIL_ACTUALIZADO", "residente", res.getId(), null);
        return aResponse(res);
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
        if (r.puedeSolicitarSolo() != null && r.puedeSolicitarSolo() != res.isPuedeSolicitarSolo()) {
            res.setPuedeSolicitarSolo(r.puedeSolicitarSolo());
            auditoria.registrar(sesion.obtener().id(), r.puedeSolicitarSolo() ? "PRIVILEGIO_PERMISOS_OTORGADO" : "PRIVILEGIO_PERMISOS_RETIRADO", "residente", id, null);
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
    public ResidenteResponse asignarHabitacion(Long residenteId, Long habitacionId, String cama) {
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
        nueva.setCama(camaLibre(hab, cama));
        nueva.setResidenteId(residenteId);
        nueva.setHabitacionId(habitacionId);
        nueva.setDesde(ahora);
        asignaciones.save(nueva);
        auditoria.registrar(sesion.obtener().id(), "HABITACION_ASIGNADA", "residente", residenteId, "habitacion=" + hab.getNumero() + "-" + nueva.getCama());
        return aResponse(res);
    }

    @Transactional
    public ApoderadoResponse agregarApoderado(Long residenteId, ApoderadoRequest r) {
        buscar(residenteId);
        String usuario = r.username() == null || r.username().isBlank() ? null : r.username().trim();
        Apoderado a = null;
        if (usuario != null) {
            var existente = usuarios.findByUsername(usuario);
            if (existente.isPresent()) {
                // Un mismo padre con varios hijos: se reutiliza su cuenta.
                if (existente.get().getRol() != Rol.APODERADO) {
                    throw new Excepciones.Conflicto("El usuario ya existe");
                }
                a = apoderados.findByUsuarioId(existente.get().getId()).orElseThrow(() -> new Excepciones.Conflicto("El usuario ya existe"));
                final Long apoderadoId = a.getId();
                if (vinculos.findByResidenteId(residenteId).stream().anyMatch(x -> x.getApoderadoId().equals(apoderadoId))) {
                    throw new Excepciones.Conflicto("Ese apoderado ya está vinculado al residente");
                }
            } else if (r.password() == null || r.password().isBlank()) {
                throw new Excepciones.SolicitudInvalida("La contraseña es obligatoria para crear la cuenta", java.util.Map.of("password", "La contraseña es obligatoria"));
            } else {
                var cuenta = usuarioService.crear(usuario, r.password(), r.nombre(), Rol.APODERADO);
                a = new Apoderado();
                a.setUsuarioId(cuenta.getId());
            }
        }
        if (a == null) {
            a = new Apoderado();
        }
        if (a.getId() == null) {
            a.setNombre(r.nombre().trim());
            a.setTelefono(r.telefono());
            a.setEmail(r.email());
            a = apoderados.save(a);
        }
        var v = new ResidenteApoderado();
        v.setResidenteId(residenteId);
        v.setApoderadoId(a.getId());
        v.setParentesco(r.parentesco());
        v.setPrincipal(r.principal());
        vinculos.save(v);
        auditoria.registrar(sesion.obtener().id(), "APODERADO_AGREGADO", "residente", residenteId, null);
        return new ApoderadoResponse(a.getId(), a.getNombre(), a.getTelefono(), a.getEmail(), v.getParentesco(), v.isPrincipal(), usuarioDe(a));
    }

    @Transactional(readOnly = true)
    public List<ApoderadoResponse> apoderados(Long residenteId) {
        buscar(residenteId);
        return vinculos.findByResidenteId(residenteId).stream().map(v -> {
            var a = apoderados.findById(v.getApoderadoId()).orElseThrow();
            return new ApoderadoResponse(a.getId(), a.getNombre(), a.getTelefono(), a.getEmail(), v.getParentesco(), v.isPrincipal(), usuarioDe(a));
        }).toList();
    }

    private String usuarioDe(Apoderado a) {
        return a.getUsuarioId() == null ? null : usuarios.findById(a.getUsuarioId()).map(Usuario::getUsername).orElse(null);
    }

    /** Letra de cama libre de la habitación: la pedida si es válida y está libre; si no, la primera libre. */
    private String camaLibre(Habitacion hab, String pedida) {
        var ocupadas = asignaciones.findByHabitacionIdAndHastaIsNull(hab.getId()).stream().map(AsignacionHabitacion::getCama).toList();
        if (pedida != null) {
            var p = pedida.trim().toUpperCase();
            boolean valida = p.length() == 1 && p.charAt(0) >= 'A' && p.charAt(0) < 'A' + hab.getCapacidad();
            if (!valida) {
                throw new Excepciones.Conflicto("La cama debe ser una letra de A a " + (char) ('A' + hab.getCapacidad() - 1));
            }
            if (ocupadas.contains(p)) {
                throw new Excepciones.Conflicto("La cama " + hab.getNumero() + "-" + p + " ya está ocupada");
            }
            return p;
        }
        for (int i = 0; i < hab.getCapacidad(); i++) {
            String letra = String.valueOf((char) ('A' + i));
            if (!ocupadas.contains(letra)) {
                return letra;
            }
        }
        throw new Excepciones.Conflicto("La habitación " + hab.getNumero() + " no tiene cupos disponibles");
    }

    private ResidenteResponse aResponse(Residente r) {
        var usuario = usuarios.findById(r.getUsuarioId()).orElseThrow();
        HabitacionResumen hab = asignaciones.findByResidenteIdAndHastaIsNull(r.getId())
                .flatMap(a -> habitaciones.findById(a.getHabitacionId()).map(h -> new HabitacionResumen(h.getId(), h.getNumero(), a.getCama()))).orElse(null);
        return new ResidenteResponse(r.getId(), r.getUsuarioId(), r.getCodigo(), usuario.getNombreCompleto(), r.getTelefono(), r.isActivo(), hab, r.isPuedeSolicitarSolo(), r.getEmail(), r.getCarrera(), r.getCiclo() == null ? null : r.getCiclo().intValue());
    }
}
