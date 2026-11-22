package pe.upeu.resi.ResiAPI.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;

public final class ReservaDtos {

    private ReservaDtos() {
    }

    /**
     * Reserva de una comida. {@code fecha} es el día en que se sirve. El motivo es obligatorio; «curso o actividad» y la
     * observación son opcionales (el desayuno frío no lleva curso).
     */
    public record ReservaRequest(
            @NotNull(message = "Elige el tipo de comida") TipoReserva tipo,
            @NotNull(message = "Indica el día de la comida") LocalDate fecha,
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 300, message = "Máximo 300 caracteres") String motivo,
            @Size(max = 100, message = "Máximo 100 caracteres") String curso,
            @Size(max = 150, message = "Máximo 150 caracteres") String observacion,
            Long evidenciaId) {
    }
}
