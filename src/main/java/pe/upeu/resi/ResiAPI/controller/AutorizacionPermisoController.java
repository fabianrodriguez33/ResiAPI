package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;
import pe.upeu.resi.ResiAPI.service.AutorizacionPermisoService;
import pe.upeu.resi.ResiAPI.service.AutorizacionPermisoService.AutorizacionResponse;

/** La administración registra y revoca las autorizaciones previas de permiso de cada estudiante. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@RequiredArgsConstructor
public class AutorizacionPermisoController {

    public record AutorizacionRequest(@NotNull TipoPermiso tipo, LocalDate inicio, LocalDate fin, @Size(max = 100) String referencia) {
    }

    public record RevocacionRequest(@Size(max = 300) String motivo) {
    }

    private final AutorizacionPermisoService servicio;

    @GetMapping("/residentes/{id}/autorizaciones")
    public List<AutorizacionResponse> listar(@PathVariable Long id) {
        return servicio.deResidente(id);
    }

    @PostMapping("/residentes/{id}/autorizaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public AutorizacionResponse registrar(@PathVariable Long id, @Valid @RequestBody AutorizacionRequest r) {
        return servicio.registrar(id, r.tipo(), r.inicio(), r.fin(), r.referencia());
    }

    @PostMapping("/autorizaciones/{id}/revocar")
    public AutorizacionResponse revocar(@PathVariable Long id, @Valid @RequestBody RevocacionRequest r) {
        return servicio.revocar(id, r.motivo());
    }
}
