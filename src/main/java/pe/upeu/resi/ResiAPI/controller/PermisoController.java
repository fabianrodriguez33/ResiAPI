package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.AmpliacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.DecisionRequest;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.JustificacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.SolicitudRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.AmpliacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.EnlaceApoderadoResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.JustificacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.SolicitudResponse;
import pe.upeu.resi.ResiAPI.enums.EstadoSolicitud;
import pe.upeu.resi.ResiAPI.service.PermisoService;

@RestController
@RequestMapping("/api/v1/permisos")
@RequiredArgsConstructor
public class PermisoController {

    private final PermisoService permisos;

    private static Pageable pagina(int pagina, int tamanio) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100));
    }

    // ---- Residente ----
    @PostMapping("/solicitudes")
    @PreAuthorize("hasRole('RESIDENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudResponse crear(@Valid @RequestBody SolicitudRequest request) {
        return permisos.crear(request);
    }

    @GetMapping("/solicitudes/mias")
    @PreAuthorize("hasRole('RESIDENTE')")
    public PaginaResponse<SolicitudResponse> mias(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return permisos.mias(pagina(pagina, tamanio));
    }

    @PostMapping("/solicitudes/{id}/cancelar")
    @PreAuthorize("hasRole('RESIDENTE')")
    public SolicitudResponse cancelar(@PathVariable Long id) {
        return permisos.cancelar(id);
    }

    @PostMapping("/solicitudes/{id}/ampliaciones")
    @PreAuthorize("hasRole('RESIDENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public AmpliacionResponse ampliar(@PathVariable Long id, @Valid @RequestBody AmpliacionRequest request) {
        return permisos.solicitarAmpliacion(id, request);
    }

    @PostMapping("/solicitudes/{id}/justificaciones")
    @PreAuthorize("hasRole('RESIDENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public JustificacionResponse justificar(@PathVariable Long id, @Valid @RequestBody JustificacionRequest request) {
        return permisos.justificar(id, request);
    }

    // ---- Residente o personal ----
    @GetMapping("/solicitudes/{id}")
    @PreAuthorize("hasAnyRole('RESIDENTE','PRECEPTOR','ADMINISTRADOR')")
    public SolicitudResponse detalle(@PathVariable Long id) {
        return permisos.detalle(id);
    }

    // ---- Preceptor / administración ----
    @GetMapping("/solicitudes")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public PaginaResponse<SolicitudResponse> listar(@RequestParam(required = false) EstadoSolicitud estado, @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "20") int tamanio) {
        return permisos.listar(estado, pagina(pagina, tamanio));
    }

    @PostMapping("/solicitudes/{id}/anular")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public SolicitudResponse anular(@PathVariable Long id, @Valid @RequestBody pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.AnulacionRequest request) {
        return permisos.anular(id, request.motivo());
    }

    @GetMapping("/solicitudes/{id}/resumen-residente")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.ResumenResidenteResponse resumenResidente(@PathVariable Long id) {
        return permisos.resumenDelResidente(id);
    }

    @PostMapping("/solicitudes/{id}/enlace-apoderado")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public EnlaceApoderadoResponse enlaceApoderado(@PathVariable Long id) {
        return permisos.generarEnlace(id);
    }

    @GetMapping("/ampliaciones/pendientes")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public PaginaResponse<AmpliacionResponse> ampliacionesPendientes(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return permisos.ampliacionesPendientes(pagina(pagina, tamanio));
    }

    @PostMapping("/ampliaciones/{id}/decision")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public AmpliacionResponse decidirAmpliacion(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return permisos.decidirAmpliacion(id, request);
    }

    @GetMapping("/justificaciones/pendientes")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public PaginaResponse<JustificacionResponse> justificacionesPendientes(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return permisos.justificacionesPendientes(pagina(pagina, tamanio));
    }

    @PostMapping("/justificaciones/{id}/decision")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public JustificacionResponse decidirJustificacion(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return permisos.decidirJustificacion(id, request);
    }
}
