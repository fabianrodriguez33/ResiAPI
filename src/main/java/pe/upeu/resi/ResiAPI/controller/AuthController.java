package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.request.LoginRequest;
import pe.upeu.resi.ResiAPI.dto.response.LoginResponse;
import pe.upeu.resi.ResiAPI.dto.response.UsuarioResponse;
import pe.upeu.resi.ResiAPI.service.AuthService;
import pe.upeu.resi.ResiAPI.service.UsuarioService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final UsuarioService usuarios;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public UsuarioResponse me() {
        return usuarios.actual();
    }
}
