package pe.upeu.resi.ResiAPI.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.PerfilDtos.ContactoRequest;
import pe.upeu.resi.ResiAPI.dto.response.PerfilResponse;
import pe.upeu.resi.ResiAPI.entity.Usuario;
import pe.upeu.resi.ResiAPI.entity.VerificacionCorreo;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.repository.VerificacionCorreoRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/** Datos personales de la cuenta (correo, teléfono, fecha de nacimiento) y verificación del correo con un código de 6 dígitos. */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(15);
    private static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);
    private static final int MAX_INTENTOS = 5;
    private static final SecureRandom AZAR = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final VerificacionCorreoRepository verificaciones;
    private final EnviadorCorreo correo;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    @Transactional(readOnly = true)
    public PerfilResponse mio() {
        return aResponse(usuario(sesion.obtener().id()));
    }

    @Transactional
    public PerfilResponse actualizarMio(ContactoRequest r) {
        var u = usuario(sesion.obtener().id());
        actualizarContacto(u, r.email(), r.telefono(), r.fechaNacimiento());
        auditoria.registrar(u.getId(), "PERFIL_ACTUALIZADO", "usuario", u.getId(), null);
        return aResponse(u);
    }

    /** Aplica los cambios de contacto. Si el correo cambia, deja de estar verificado y se descarta el código pendiente. */
    @Transactional
    public void actualizarContacto(Usuario u, String email, String telefono, LocalDate nacimiento) {
        if (email != null) {
            String nuevo = email.isBlank() ? null : email.trim();
            boolean igual = nuevo == null ? u.getEmail() == null : nuevo.equalsIgnoreCase(u.getEmail());
            if (!igual) {
                u.setEmail(nuevo);
                u.setEmailVerificado(false);
                verificaciones.deleteById(u.getId());
            }
        }
        if (telefono != null) {
            u.setTelefono(telefono.isBlank() ? null : telefono.trim());
        }
        if (nacimiento != null) {
            validarNacimiento(nacimiento);
            u.setFechaNacimiento(nacimiento);
        }
    }

    public void validarNacimiento(LocalDate nacimiento) {
        if (!nacimiento.isBefore(reloj.instant().atZone(zona).toLocalDate()) || nacimiento.getYear() < 1900) {
            throw new Excepciones.SolicitudInvalida("Fecha de nacimiento inválida", java.util.Map.of("fechaNacimiento", "Escribe una fecha de nacimiento válida"));
        }
    }

    /** Envía un código al correo actual del usuario. Se puede pedir otro pasado un minuto. */
    @Transactional
    public void enviarCodigo() {
        var u = usuario(sesion.obtener().id());
        if (u.getEmail() == null) {
            throw new Excepciones.Conflicto("Primero guarda tu correo electrónico");
        }
        if (u.isEmailVerificado()) {
            throw new Excepciones.Conflicto("Tu correo ya está verificado");
        }
        var ahora = reloj.instant();
        var previa = verificaciones.findById(u.getId());
        if (previa.isPresent() && previa.get().getCorreo().equalsIgnoreCase(u.getEmail()) && previa.get().getEnviadoEn().plus(ESPERA_REENVIO).isAfter(ahora)) {
            throw new Excepciones.Conflicto("Espera un minuto antes de pedir otro código");
        }
        String codigo = String.format("%06d", AZAR.nextInt(1_000_000));
        var v = previa.orElseGet(VerificacionCorreo::new);
        v.setUsuarioId(u.getId());
        v.setCorreo(u.getEmail());
        v.setCodigoHash(hash(u.getId(), codigo));
        v.setEnviadoEn(ahora);
        v.setExpiraEn(ahora.plus(VIGENCIA_CODIGO));
        v.setIntentos((short) 0);
        verificaciones.save(v);
        correo.enviarCodigo(u.getEmail(), codigo);
        auditoria.registrar(u.getId(), "CORREO_CODIGO_ENVIADO", "usuario", u.getId(), null);
    }

    /** Los intentos fallidos se guardan aunque se rechace la solicitud, por eso no se revierte la transacción. */
    @Transactional(noRollbackFor = RuntimeException.class)
    public PerfilResponse verificar(String codigo) {
        var u = usuario(sesion.obtener().id());
        if (u.isEmailVerificado()) {
            return aResponse(u);
        }
        var v = verificaciones.findById(u.getId()).filter(x -> u.getEmail() != null && x.getCorreo().equalsIgnoreCase(u.getEmail()))
                .orElseThrow(() -> new Excepciones.Conflicto("Pide primero un código de verificación"));
        if (!v.getExpiraEn().isAfter(reloj.instant())) {
            verificaciones.delete(v);
            throw new Excepciones.Conflicto("El código venció. Pide uno nuevo.");
        }
        if (v.getIntentos() >= MAX_INTENTOS) {
            verificaciones.delete(v);
            throw new Excepciones.Conflicto("Demasiados intentos. Pide un código nuevo.");
        }
        if (!MessageDigest.isEqual(v.getCodigoHash().getBytes(StandardCharsets.UTF_8), hash(u.getId(), codigo).getBytes(StandardCharsets.UTF_8))) {
            v.setIntentos((short) (v.getIntentos() + 1));
            throw new Excepciones.Conflicto("El código no es correcto");
        }
        if (usuarios.existsByEmailIgnoreCaseAndEmailVerificadoTrueAndIdNot(u.getEmail(), u.getId())) {
            throw new Excepciones.Conflicto("Ese correo ya está verificado en otra cuenta");
        }
        u.setEmailVerificado(true);
        verificaciones.delete(v);
        auditoria.registrar(u.getId(), "CORREO_VERIFICADO", "usuario", u.getId(), null);
        return aResponse(u);
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Usuario no encontrado"));
    }

    private static String hash(Long usuarioId, String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((usuarioId + ":" + codigo).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PerfilResponse aResponse(Usuario u) {
        return new PerfilResponse(u.getId(), u.getUsername(), u.getNombreCompleto(), u.getRol(), u.getDni(), u.getFechaNacimiento(),
                u.getEmail(), u.isEmailVerificado(), u.getTelefono());
    }
}
