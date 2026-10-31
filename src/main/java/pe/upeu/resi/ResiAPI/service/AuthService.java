package pe.upeu.resi.ResiAPI.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.LoginRequest;
import pe.upeu.resi.ResiAPI.dto.response.LoginResponse;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.TokenService;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Hash válido de una clave inexistente: iguala el tiempo de respuesta cuando el usuario no existe. */
    private static final String HASH_FICTICIO = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BUXpMQRvF.W8TQLMlYwI1rQjUBe2";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        var usuario = usuarios.findByUsername(request.username().trim()).orElse(null);
        boolean coincide = encoder.matches(request.password(), usuario != null ? usuario.getPasswordHash() : HASH_FICTICIO);
        if (usuario == null || !coincide || !usuario.isActivo()) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        return tokens.emitir(usuario);
    }
}
