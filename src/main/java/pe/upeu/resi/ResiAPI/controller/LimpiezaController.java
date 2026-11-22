package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.request.LimpiezaHabitacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.LimpiezaRequest;
import pe.upeu.resi.ResiAPI.dto.response.LimpiezaResponses.HabitacionLimpiezaResponse;
import pe.upeu.resi.ResiAPI.dto.response.LimpiezaResponses.EvaluacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.LimpiezaResponses.ResumenLimpiezaResponse;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.service.LimpiezaService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LimpiezaController {

    private final LimpiezaService limpieza;

    @PostMapping("/limpieza/evaluaciones")
    @PreAuthorize("hasRole('PRECEPTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluacionResponse registrar(@Valid @RequestBody LimpiezaRequest request) {
        return limpieza.registrar(request);
    }

    @GetMapping("/limpieza/habitaciones")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public java.util.List<HabitacionLimpiezaResponse> habitaciones(@RequestParam(required = false) java.time.LocalDate fecha) {
        return limpieza.habitaciones(fecha);
    }

    public record EliminarEvaluacionRequest(@jakarta.validation.constraints.NotNull java.time.LocalDate fecha, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String motivo) {
    }

    @GetMapping("/limpieza/habitaciones/{id}/evaluacion")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public pe.upeu.resi.ResiAPI.service.LimpiezaService.EvaluacionHabitacionDetalle detalle(@org.springframework.web.bind.annotation.PathVariable Long id,
                                                                                         @RequestParam java.time.LocalDate fecha) {
        return limpieza.detalleDeHabitacion(id, fecha);
    }

    @PostMapping("/limpieza/habitaciones/{id}/reemplazar")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public java.util.List<EvaluacionResponse> reemplazar(@org.springframework.web.bind.annotation.PathVariable Long id, @Valid @RequestBody LimpiezaHabitacionRequest request) {
        return limpieza.reemplazarHabitacion(id, request);
    }

    public record EliminarDiaRequest(@jakarta.validation.constraints.NotNull java.time.LocalDate fecha, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String motivo) {
    }

    @PostMapping("/limpieza/eliminar-dia")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public java.util.Map<String, Integer> eliminarDia(@Valid @RequestBody EliminarDiaRequest r) {
        return java.util.Map.of("eliminadas", limpieza.eliminarDelDia(r.fecha(), r.motivo()));
    }

    @PostMapping("/limpieza/habitaciones/{id}/eliminar")
    @PreAuthorize("hasRole('PRECEPTOR')")
    public java.util.Map<String, Integer> eliminar(@org.springframework.web.bind.annotation.PathVariable Long id, @Valid @RequestBody EliminarEvaluacionRequest r) {
        return java.util.Map.of("eliminadas", limpieza.eliminarDeHabitacion(id, r.fecha(), r.motivo()));
    }

    @PostMapping("/limpieza/habitaciones/{id}/evaluaciones")
    @PreAuthorize("hasRole('PRECEPTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public java.util.List<EvaluacionResponse> registrarHabitacion(@org.springframework.web.bind.annotation.PathVariable Long id,
                                                                  @Valid @RequestBody LimpiezaHabitacionRequest request) {
        return limpieza.registrarHabitacion(id, request);
    }

    @GetMapping("/limpieza/evaluaciones")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public PaginaResponse<EvaluacionResponse> deResidente(@RequestParam Long residenteId, @RequestParam(defaultValue = "0") int pagina,
                                                          @RequestParam(defaultValue = "20") int tamanio) {
        return limpieza.deResidente(residenteId, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }

    @GetMapping("/residentes/yo/limpieza")
    @PreAuthorize("hasRole('RESIDENTE')")
    public PaginaResponse<EvaluacionResponse> mias(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return limpieza.mias(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }

    @GetMapping("/residentes/yo/limpieza/resumen")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResumenLimpiezaResponse resumen() {
        return limpieza.resumenMio();
    }
}
