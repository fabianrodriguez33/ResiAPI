package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
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
import pe.upeu.resi.ResiAPI.dto.request.ReservaDtos.ReservaRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.OfertaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.ReservaResponse;
import pe.upeu.resi.ResiAPI.service.ReservaAlimentacionService;

/** Reservas de alimentación: lo que hace el residente con las suyas. */
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaAlimentacionController {

    private final ReservaAlimentacionService reservas;

    /** Las comidas que puede pedir hoy y su situación (abierta, vencida, día cerrado, ya reservada). */
    @GetMapping("/oferta")
    @PreAuthorize("hasRole('RESIDENTE')")
    public OfertaResponse oferta() {
        return reservas.oferta();
    }

    @PostMapping
    @PreAuthorize("hasRole('RESIDENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse reservar(@Valid @RequestBody ReservaRequest request) {
        return reservas.reservar(request);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ReservaResponse cancelar(@PathVariable Long id) {
        return reservas.cancelar(id);
    }

    /** Su historial: reservas activas y canceladas, de la más reciente a la más antigua. */
    @GetMapping("/mias")
    @PreAuthorize("hasRole('RESIDENTE')")
    public PaginaResponse<ReservaResponse> mias(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return reservas.mias(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }

    // ---- Pastor / monitor ----

    /** Reservas de una fecha, de todos los tipos o de uno solo. */
    @GetMapping("/del-dia")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public java.util.List<pe.upeu.resi.ResiAPI.dto.response.ReservaResponses.ReservaPastorResponse> delDia(
            @RequestParam java.time.LocalDate fecha, @RequestParam(required = false) pe.upeu.resi.ResiAPI.enums.TipoReserva tipo) {
        return reservas.delDia(fecha, tipo);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public ReservaResponse decidir(@PathVariable Long id, @Valid @RequestBody pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.DecisionRequest request) {
        return reservas.decidir(id, request.aprobar(), request.comentario());
    }
}
