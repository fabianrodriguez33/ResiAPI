package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Evaluación de una habitación: basura y olores son comunes a toda la habitación; ropero, cama, escritorio y piso
 * son personales. Se crea una evaluación por residente con los seis criterios (total sobre 20).
 */
public record LimpiezaHabitacionRequest(
        @NotNull(message = "La fecha es obligatoria") LocalDate fecha,
        @NotNull(message = "El puntaje de basura es obligatorio") Integer basura,
        @NotNull(message = "El puntaje de olores es obligatorio") Integer olores,
        @NotEmpty(message = "Indique al menos un residente") List<@Valid ResidentePuntaje> residentes) {

    public record ResidentePuntaje(
            @NotNull(message = "El residente es obligatorio") Long residenteId,
            @NotNull(message = "El puntaje de ropero es obligatorio") Integer ropero,
            @NotNull(message = "El puntaje de cama es obligatorio") Integer cama,
            @NotNull(message = "El puntaje de escritorio es obligatorio") Integer escritorio,
            @NotNull(message = "El puntaje de piso es obligatorio") Integer piso,
            @Size(max = 500, message = "Máximo 500 caracteres") String observacion) {
    }
}
