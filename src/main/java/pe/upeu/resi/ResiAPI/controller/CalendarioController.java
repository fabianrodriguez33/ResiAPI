package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.service.CalendarioService;
import pe.upeu.resi.ResiAPI.service.CalendarioService.DiaSinCultoResponse;

@RestController
@RequestMapping("/api/v1/calendario/dias-sin-culto")
@RequiredArgsConstructor
public class CalendarioController {

    public record DiaSinCultoRequest(@NotNull(message = "La fecha es obligatoria") LocalDate fecha,
                                     @NotBlank(message = "El motivo es obligatorio") @Size(max = 120, message = "Máximo 120 caracteres") String motivo) {
    }

    private final CalendarioService calendario;

    /** Cualquier usuario autenticado: la app lo necesita para calcular la racha de cultos. */
    @GetMapping
    public List<DiaSinCultoResponse> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return calendario.listar(desde, hasta);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public DiaSinCultoResponse marcar(@Valid @RequestBody DiaSinCultoRequest request) {
        return calendario.marcar(request.fecha(), request.motivo());
    }

    @DeleteMapping("/{fecha}")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        calendario.quitar(fecha);
    }
}
