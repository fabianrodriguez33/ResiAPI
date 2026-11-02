package pe.upeu.resi.ResiAPI.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.LoginRequest;
import pe.upeu.resi.ResiAPI.dto.response.LoginResponse;
import pe.upeu.resi.ResiAPI.entity.Usuario;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.LimitadorLogin;
import pe.upeu.resi.ResiAPI.security.SesionActual;
import pe.upeu.resi.ResiAPI.security.TokenService;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Hash válido de una clave inexistente: iguala el tiempo de respuesta cuando el usuario no existe. */
    private static final String HASH_FICTICIO = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BUXpMQRvF.W8TQLMlYwI1rQjUBe2";
    private static final Duration VIGENCIA_PROVISIONAL = Duration.ofMinutes(30);
    private static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofMinutes(1);
    /** Sin letras ni números que se confundan (0/O, 1/l/I). */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LimitadorLogin limitador;
    private final EnviadorCorreo correo;
    private final SesionActual sesion;
    private final Clock reloj;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String nombre = request.username().trim();
        limitador.verificar(nombre);
        var usuario = usuarios.findByUsername(nombre).orElse(null);
        boolean coincide = usuario != null && encoder.matches(request.password(), usuario.getPasswordHash());
        boolean provisional = !coincide && usuario != null && coincideProvisional(usuario, request.password());
        if (usuario == null) {
            encoder.matches(request.password(), HASH_FICTICIO); // iguala el tiempo de respuesta
        }
        if (usuario == null || !(coincide || provisional) || !usuario.isActivo()) {
            limitador.registrarFallo(nombre);
            throw new BadCredentialsException("Credenciales inválidas");
        }
        limitador.registrarAcierto(nombre);
        return tokens.emitir(usuario, provisional);
    }

    /**
     * Quien olvidó su contraseña recibe una provisional en su correo verificado. La contraseña real sigue valiendo; la
     * provisional vence a los 30 minutos y se pide cambiarla al entrar. La respuesta es siempre la misma: no revela qué
     * usuarios existen ni cuáles tienen correo.
     */
    @Transactional
    public void olvideClave(String identificador) {
        var usuario = usuarios.findByUsername(identificador.trim()).orElse(null);
        if (usuario == null || !usuario.isActivo() || !usuario.isEmailVerificado() || usuario.getEmail() == null) {
            return;
        }
        var ahora = reloj.instant();
        if (usuario.getClaveProvisionalEnviada() != null && usuario.getClaveProvisionalEnviada().plus(ESPERA_ENTRE_ENVIOS).isAfter(ahora)) {
            return;
        }
        var clave = new StringBuilder();
        for (int i = 0; i < 10; i++) clave.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        usuario.setClaveProvisionalHash(encoder.encode(clave));
        usuario.setClaveProvisionalExpira(ahora.plus(VIGENCIA_PROVISIONAL));
        usuario.setClaveProvisionalEnviada(ahora);
        correo.enviar(usuario.getEmail(), "tu contraseña provisional",
                "Tu contraseña provisional es " + clave + ". Vale 30 minutos: entra a ResiUP con ella y la app te pedirá elegir una nueva. "
                        + "Si no la pediste, ignora este mensaje: tu contraseña de siempre sigue funcionando.");
    }

    /** Cambia la contraseña del usuario de la sesión; sirve tanto con la contraseña real como con la provisional. */
    @Transactional
    public void cambiarClave(String actual, String nueva) {
        var usuario = usuarios.findById(sesion.obtener().id()).orElseThrow();
        limitador.verificar(usuario.getUsername());
        if (!encoder.matches(actual, usuario.getPasswordHash()) && !coincideProvisional(usuario, actual)) {
            limitador.registrarFallo(usuario.getUsername());
            throw new Excepciones.SolicitudInvalida("La contraseña actual no es correcta", java.util.Map.of("claveActual", "No es la contraseña actual"));
        }
        if (nueva.equals(actual)) {
            throw new Excepciones.SolicitudInvalida("Elige una contraseña distinta", java.util.Map.of("claveNueva", "Debe ser distinta de la actual"));
        }
        limitador.registrarAcierto(usuario.getUsername());
        usuario.setPasswordHash(encoder.encode(nueva));
        limpiarProvisional(usuario);
    }

    private boolean coincideProvisional(Usuario u, String clave) {
        return u.getClaveProvisionalHash() != null && u.getClaveProvisionalExpira() != null
                && u.getClaveProvisionalExpira().isAfter(reloj.instant()) && encoder.matches(clave, u.getClaveProvisionalHash());
    }

    private static void limpiarProvisional(Usuario u) {
        u.setClaveProvisionalHash(null);
        u.setClaveProvisionalExpira(null);
    }
}
