package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ApoderadoRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.PerfilRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.AsignacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.HabitacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ResidenteActualizarRequest;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.ResidenteRequest;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.ApoderadoResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.HabitacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.OcupacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.QrResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.ResidenteResponse;
import pe.upeu.resi.ResiAPI.security.SesionActual;
import pe.upeu.resi.ResiAPI.service.HabitacionService;
import pe.upeu.resi.ResiAPI.service.QrService;
import pe.upeu.resi.ResiAPI.service.ResidenteService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResidenteController {

    private final ResidenteService residentes;
    private final HabitacionService habitaciones;
    private final QrService qr;
    private final SesionActual sesion;

    // ---- Habitaciones ----
    @GetMapping("/habitaciones")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','PRECEPTOR')")
    public List<HabitacionResponse> listarHabitaciones() {
        return habitaciones.listar();
    }

    @GetMapping("/habitaciones/ocupacion")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','PRECEPTOR')")
    public OcupacionResponse ocupacion() {
        return habitaciones.ocupacion();
    }

    @PostMapping("/habitaciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public HabitacionResponse crearHabitacion(@Valid @RequestBody HabitacionRequest request) {
        return habitaciones.crear(request);
    }

    @PutMapping("/habitaciones/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public HabitacionResponse actualizarHabitacion(@PathVariable Long id, @Valid @RequestBody HabitacionRequest request) {
        return habitaciones.actualizar(id, request);
    }

    // ---- Residente autenticado ----
    @GetMapping("/residentes/yo")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResidenteResponse yo() {
        return residentes.yo();
    }

    @PatchMapping("/residentes/yo")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResidenteResponse actualizarMiPerfil(@Valid @RequestBody PerfilRequest request) {
        return residentes.actualizarPerfil(request);
    }

    @GetMapping("/residentes/yo/qr")
    @PreAuthorize("hasRole('RESIDENTE')")
    public QrResponse miQr() {
        return qr.contenidoVigente(residentes.residenteActual().getId());
    }

    // ---- Gestión de residentes ----
    @GetMapping("/residentes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','PRECEPTOR')")
    public PaginaResponse<ResidenteResponse> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return residentes.listar(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }

    @GetMapping("/residentes/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','PRECEPTOR')")
    public ResidenteResponse obtener(@PathVariable Long id) {
        return residentes.obtener(id);
    }

    @PostMapping("/residentes")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResidenteResponse crear(@Valid @RequestBody ResidenteRequest request) {
        return residentes.crear(request);
    }

    @PatchMapping("/residentes/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResidenteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ResidenteActualizarRequest request) {
        return residentes.actualizar(id, request);
    }

    @PostMapping("/residentes/{id}/asignacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResidenteResponse asignar(@PathVariable Long id, @Valid @RequestBody AsignacionRequest request) {
        return residentes.asignarHabitacion(id, request.habitacionId(), request.cama());
    }

    @GetMapping("/residentes/{id}/apoderados")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','PRECEPTOR')")
    public List<ApoderadoResponse> apoderados(@PathVariable Long id) {
        return residentes.apoderados(id);
    }

    @PostMapping("/residentes/{id}/apoderados")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApoderadoResponse agregarApoderado(@PathVariable Long id, @Valid @RequestBody ApoderadoRequest request) {
        return residentes.agregarApoderado(id, request);
    }

    @PostMapping("/residentes/{id}/qr/regenerar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public QrResponse regenerarQr(@PathVariable Long id) {
        return qr.regenerar(id, sesion.obtener().id());
    }
}
