package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import pe.upeu.resi.ResiAPI.enums.CriterioLimpieza;

/** El total se calcula en el servidor; el cliente sólo envía el puntaje por criterio. */
public record LimpiezaRequest(
        @NotNull(message = "El residente es obligatorio") Long residenteId,
        @NotNull(message = "La fecha es obligatoria") LocalDate fecha,
        @NotEmpty(message = "Debe indicar el puntaje de cada criterio") Map<CriterioLimpieza, Integer> puntajes,
        @Size(max = 500, message = "Máximo 500 caracteres") String observacion) {
}
