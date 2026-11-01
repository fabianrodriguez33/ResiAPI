package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.UsuarioActualizarRequest;
import pe.upeu.resi.ResiAPI.dto.request.UsuarioRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.UsuarioResponse;
import pe.upeu.resi.ResiAPI.entity.Usuario;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.mapper.UsuarioMapper;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final PerfilService perfil;

    @Transactional(readOnly = true)
    public UsuarioResponse actual() {
        return usuarios.findById(sesion.obtener().id()).map(UsuarioMapper::aResponse)
                .orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Usuario no encontrado"));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(Pageable pageable) {
        return PaginaResponse.de(usuarios.findAllByOrderByNombreCompletoAsc(pageable), UsuarioMapper::aResponse);
    }

    /**
     * Crea personal: pastor (preceptor) o garita. Los residentes se crean desde /residentes, las cuentas de los padres
     * desde la ficha de cada residente, y el administrador inicial sale de la configuración del servidor.
     */
    @Transactional
    public UsuarioResponse crearPersonal(UsuarioRequest r) {
        if (r.rol() != Rol.PRECEPTOR && r.rol() != Rol.GARITA) {
            throw new Excepciones.SolicitudInvalida("Desde aquí solo se crean pastores y personal de garita",
                    java.util.Map.of("rol", "Elige Pastor (PRECEPTOR) o Garita"));
        }
        var u = crear(r.username(), r.password(), r.nombreCompleto(), r.rol());
        asignarIdentidad(u, r.dni(), r.fechaNacimiento());
        return aResponse(u);
    }

    /** Fija el DNI (único) y la fecha de nacimiento. Solo lo hace la administración. */
    public void asignarIdentidad(Usuario u, String dni, java.time.LocalDate nacimiento) {
        if (dni != null && !dni.equals(u.getDni())) {
            if (usuarios.existsByDniAndIdNot(dni, u.getId())) {
                throw new Excepciones.Conflicto("Ya existe una persona con el DNI " + dni);
            }
            u.setDni(dni);
        }
        if (nacimiento != null) {
            perfil.validarNacimiento(nacimiento);
            u.setFechaNacimiento(nacimiento);
        }
    }

    /** Uso interno: crea un usuario validando unicidad y aplicando BCrypt. */
    @Transactional
    public Usuario crear(String username, String password, String nombreCompleto, Rol rol) {
        String nombreUsuario = username.trim();
        if (usuarios.existsByUsername(nombreUsuario)) {
            throw new Excepciones.Conflicto("El usuario ya existe");
        }
        var u = new Usuario();
        u.setUsername(nombreUsuario);
        u.setPasswordHash(encoder.encode(password));
        u.setNombreCompleto(nombreCompleto.trim());
        u.setRol(rol);
        u.setActivo(true);
        u.setCreadoEn(reloj.instant());
        u = usuarios.save(u);
        auditoria.registrar(actorId(), "USUARIO_CREADO", "usuario", u.getId(), "rol=" + rol);
        return u;
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioActualizarRequest r) {
        var u = usuarios.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Usuario no encontrado"));
        if (r.nombreCompleto() != null && !r.nombreCompleto().isBlank()) {
            u.setNombreCompleto(r.nombreCompleto().trim());
        }
        asignarIdentidad(u, r.dni(), r.fechaNacimiento());
        if (r.password() != null) {
            u.setPasswordHash(encoder.encode(r.password()));
            auditoria.registrar(actorId(), "USUARIO_CLAVE_CAMBIADA", "usuario", id, null);
        }
        if (r.activo() != null && r.activo() != u.isActivo()) {
            if (!r.activo() && id.equals(actorId())) {
                throw new Excepciones.Conflicto("No puedes desactivar tu propia cuenta");
            }
            u.setActivo(r.activo());
            auditoria.registrar(actorId(), r.activo() ? "USUARIO_ACTIVADO" : "USUARIO_DESACTIVADO", "usuario", id, null);
        }
        return aResponse(u);
    }

    private Long actorId() {
        try {
            return sesion.obtener().id();
        } catch (IllegalStateException e) {
            return null; // operación de arranque (administrador inicial)
        }
    }

    private static UsuarioResponse aResponse(Usuario u) {
        return UsuarioMapper.aResponse(u);
    }
}
