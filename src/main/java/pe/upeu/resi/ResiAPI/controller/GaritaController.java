package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.response.GaritaResponses.MovimientoResponse;
import pe.upeu.resi.ResiAPI.dto.response.GaritaResponses.VerificacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.enums.TipoMovimiento;
import pe.upeu.resi.ResiAPI.service.GaritaService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GaritaController {

    public record VerificarRequest(@NotBlank(message = "El QR es obligatorio") @Size(max = 120) String qr) {
    }

    public record MovimientoRequest(@NotBlank(message = "El QR es obligatorio") @Size(max = 120) String qr,
                                    @NotNull(message = "El tipo de movimiento es obligatorio") TipoMovimiento tipo) {
    }

    public record AnularMovimientoRequest(@NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo) {
    }

    private final GaritaService garita;

    @PostMapping("/garita/verificaciones")
    @PreAuthorize("hasAnyRole('GARITA','PRECEPTOR','ADMINISTRADOR')")
    public VerificacionResponse verificar(@Valid @RequestBody VerificarRequest request) {
        return garita.verificar(request.qr());
    }

    @PostMapping("/garita/movimientos")
    @PreAuthorize("hasRole('GARITA')")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse registrar(@Valid @RequestBody MovimientoRequest request) {
        return garita.registrar(request.qr(), request.tipo());
    }

    @GetMapping("/garita/movimientos")
    @PreAuthorize("hasAnyRole('GARITA','PRECEPTOR','ADMINISTRADOR')")
    public PaginaResponse<MovimientoResponse> historial(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return garita.historial(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }

    @PostMapping("/garita/movimientos/{id}/anular")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public MovimientoResponse anular(@PathVariable Long id, @Valid @RequestBody AnularMovimientoRequest request) {
        return garita.anular(id, request.motivo());
    }

    @GetMapping("/residentes/yo/movimientos")
    @PreAuthorize("hasRole('RESIDENTE')")
    public PaginaResponse<MovimientoResponse> mios(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return garita.mios(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }
}
