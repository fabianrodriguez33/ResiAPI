package pe.upeu.resi.ResiAPI.dto.response;

import java.time.Instant;
import pe.upeu.resi.ResiAPI.enums.TipoMovimiento;

public final class GaritaResponses {

    private GaritaResponses() {
    }

    public enum Resultado {
        AUTORIZADO_SALIDA, AUTORIZADO_RETORNO, RETORNO_FUERA_DE_HORARIO,
        QR_INVALIDO, QR_REVOCADO, RESIDENTE_INACTIVO,
        SIN_PERMISO, PERMISO_AUN_NO_VIGENTE, PERMISO_VENCIDO
    }

    /** Datos mínimos para que garita identifique al residente. */
    public record ResidenteGarita(Long id, String codigo, String nombreCompleto, String habitacion) {
    }

    public record VerificacionResponse(Resultado resultado, String mensaje, ResidenteGarita residente, TipoMovimiento movimientoPermitido,
                                       Long solicitudId, Instant retornoAutorizado, Integer minutosTarde) {
    }

    /** Un permiso aprobado del residente que garita puede usar: {@code salidaAbierta} = ya salió y falta registrar su retorno. */
    public record PermisoGarita(Long id, pe.upeu.resi.ResiAPI.enums.TipoPermiso tipo, String destino, String motivo, Instant salida, Instant retorno, boolean salidaAbierta) {
    }

    /** Al escanear el QR (o digitar el código): el residente y sus permisos aprobados que aún no tienen el retorno registrado. */
    public record PermisosDeResidente(Resultado resultado, String mensaje, ResidenteGarita residente, java.util.List<PermisoGarita> permisos) {
    }

    public record MovimientoResponse(Long id, Long residenteId, String codigo, String nombreCompleto, Long solicitudId, TipoMovimiento tipo,
                                     Instant registradoEn, Integer minutosTarde, boolean anulado, String motivoAnulacion) {
    }
}
