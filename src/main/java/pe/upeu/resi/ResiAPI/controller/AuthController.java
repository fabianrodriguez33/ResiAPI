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

    /** Responde siempre lo mismo, exista o no el usuario y tenga o no correo verificado. */
    @PostMapping("/olvide-clave")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void olvideClave(@Valid @RequestBody pe.upeu.resi.ResiAPI.dto.request.ClaveDtos.OlvideClaveRequest request) {
        auth.olvideClave(request.username());
    }

    @PostMapping("/cambiar-clave")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void cambiarClave(@Valid @RequestBody pe.upeu.resi.ResiAPI.dto.request.ClaveDtos.CambiarClaveRequest request) {
        auth.cambiarClave(request.claveActual(), request.claveNueva());
    }

    @GetMapping("/me")
    public UsuarioResponse me() {
        return usuarios.actual();
    }
}
