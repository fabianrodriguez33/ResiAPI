package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.request.PerfilDtos.CodigoCorreoRequest;
import pe.upeu.resi.ResiAPI.dto.request.PerfilDtos.ContactoRequest;
import pe.upeu.resi.ResiAPI.dto.response.PerfilResponse;
import pe.upeu.resi.ResiAPI.service.PerfilService;

/** Datos personales de la cuenta para cualquier rol. El DNI y el código universitario los gestiona solo la administración. */
@RestController
@RequestMapping("/api/v1/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfil;

    @GetMapping
    public PerfilResponse mio() {
        return perfil.mio();
    }

    @PatchMapping
    public PerfilResponse actualizar(@Valid @RequestBody ContactoRequest request) {
        return perfil.actualizarMio(request);
    }

    @PostMapping("/correo/codigo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarCodigo() {
        perfil.enviarCodigo();
    }

    @PostMapping("/correo/verificar")
    public PerfilResponse verificar(@Valid @RequestBody CodigoCorreoRequest request) {
        return perfil.verificar(request.codigo());
    }
}
