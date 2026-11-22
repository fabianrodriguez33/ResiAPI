package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.service.SemestreService;
import pe.upeu.resi.ResiAPI.service.SemestreService.SemestreResponse;

@RestController
@RequestMapping("/api/v1/semestres")
@RequiredArgsConstructor
public class SemestreController {

    public record SemestreRequest(String nombre, LocalDate inicio, LocalDate fin) {
    }

    private final SemestreService servicio;

    /** Cualquier usuario autenticado ve el semestre en curso (se muestra en su inicio). */
    @GetMapping("/actual")
    public SemestreResponse actual() {
        return servicio.actual();
    }

    @GetMapping
    public List<SemestreResponse> listar() {
        return servicio.listar();
    }

    /** Crea el semestre siguiente: cierra el actual y deja todas las habitaciones vacías. Solo la administración. */
    @PostMapping("/terminar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public SemestreResponse terminar() {
        return servicio.terminarActual();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public SemestreResponse crear(@Valid @RequestBody SemestreRequest r) {
        return servicio.crear(r.nombre(), r.inicio(), r.fin());
    }
}
