package pe.upeu.resi.ResiAPI.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import pe.upeu.resi.ResiAPI.enums.EstadoReserva;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;

public final class ReservaResponses {

    private ReservaResponses() {
    }

    /** Un permiso aprobado, no anulado, cuya vigencia toca el día de la comida. Solo avisa: nunca bloquea la reserva. */
    public record AdvertenciaPermiso(String mensaje, TipoPermiso tipo, Instant salida, Instant retorno) {
    }

    /** {@code advertenciaPermiso} viene solo en la respuesta de reservar y en la oferta. */
    public record ReservaResponse(Long id, TipoReserva tipo, LocalDate fecha, String motivo, String curso, String observacion,
                                  EstadoReserva estado, Instant creadaEn, Instant canceladaEn, String motivoCancelacion,
                                  AdvertenciaPermiso advertenciaPermiso) {
    }

    public enum EstadoOferta {
        ABIERTA, NO_ABRIO, VENCIDA, DIA_CERRADO
    }

    /**
     * Una comida que el residente puede pedir hoy (la de hoy; la de mañana para el desayuno frío). {@code puedeReservar} ya
     * considera plazo, día cerrado y reserva activa; si es falso, {@code motivoNoDisponible} lo explica.
     */
    public record OfertaItem(TipoReserva tipo, LocalDate fecha, Instant abre, Instant cierra, EstadoOferta estado, boolean puedeReservar,
                             String motivoNoDisponible, String motivoCierre, ReservaResponse reservaActiva, AdvertenciaPermiso advertenciaPermiso) {
    }

    public record OfertaResponse(LocalDate hoy, List<OfertaItem> comidas) {
    }
}
