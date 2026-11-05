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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.service.HabilitacionService;

/** Habilitación semanal para solicitar permisos y justificación de faltas a cultos. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HabilitacionController {

    public record JustificarFaltaRequest(@NotNull(message = "Indica el residente") Long residenteId,
                                         @NotNull(message = "Indica el día de la falta") LocalDate fecha,
                                         @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {
    }

    private final HabilitacionService habilitacion;

    @GetMapping("/residentes/yo/habilitacion")
    @PreAuthorize("hasRole('RESIDENTE')")
    public HabilitacionService.Estado mia() {
        return habilitacion.deMiResidente();
    }

    @GetMapping("/habilitacion/faltas-por-justificar")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public List<HabilitacionService.FaltaPendiente> porJustificar() {
        return habilitacion.faltasPorJustificar();
    }

    @PostMapping("/habilitacion/faltas/justificar")
    @PreAuthorize("hasRole('PRECEPTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void justificar(@Valid @RequestBody JustificarFaltaRequest request) {
        habilitacion.justificar(request.residenteId(), request.fecha(), request.motivo());
    }
}
