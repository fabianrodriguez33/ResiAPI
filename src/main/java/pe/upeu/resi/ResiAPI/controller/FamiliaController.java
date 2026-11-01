package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import java.util.List;
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
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.DecisionApoderadoRequest;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.SolicitudRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.HijoResponse;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.SolicitudResponse;
import pe.upeu.resi.ResiAPI.enums.EstadoSolicitud;
import pe.upeu.resi.ResiAPI.service.PermisoService;

/** Perfil de padres de familia: aceptan los permisos de sus hijos y envían los de pernoctar fuera. */
@RestController
@RequestMapping("/api/v1/familia")
@RequiredArgsConstructor
@PreAuthorize("hasRole('APODERADO')")
public class FamiliaController {

    private final PermisoService permisos;

    @GetMapping("/hijos")
    public List<HijoResponse> hijos() {
        return permisos.hijos();
    }

    @GetMapping("/solicitudes")
    public PaginaResponse<SolicitudResponse> solicitudes(@RequestParam(required = false) EstadoSolicitud estado, @RequestParam(defaultValue = "0") int pagina,
                                                         @RequestParam(defaultValue = "20") int tamanio) {
        return permisos.deMisHijos(estado, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }

    @GetMapping("/solicitudes/{id}")
    public SolicitudResponse detalle(@PathVariable Long id) {
        return permisos.detalleDeHijo(id);
    }

    @PostMapping("/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudResponse crear(@Valid @RequestBody SolicitudRequest request) {
        return permisos.crearComoApoderado(request);
    }

    @PostMapping("/solicitudes/{id}/decision")
    public SolicitudResponse decidir(@PathVariable Long id, @Valid @RequestBody DecisionApoderadoRequest request) {
        return permisos.decidirComoApoderado(id, request.aprobar());
    }

    @PostMapping("/solicitudes/{id}/cancelar")
    public SolicitudResponse cancelar(@PathVariable Long id) {
        return permisos.cancelarComoApoderado(id);
    }
}
