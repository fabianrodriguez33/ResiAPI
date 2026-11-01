package pe.upeu.resi.ResiAPI.exception;

import java.util.Map;

/** Excepciones de negocio mapeadas a códigos HTTP por {@link GlobalExceptionHandler}. */
public final class Excepciones {

    private Excepciones() {
    }

    /** 404 */
    public static class RecursoNoEncontrado extends RuntimeException {
        public RecursoNoEncontrado(String mensaje) {
            super(mensaje);
        }
    }

    /** 409: la operación choca con una regla de negocio o con el estado actual. */
    public static class Conflicto extends RuntimeException {
        public Conflicto(String mensaje) {
            super(mensaje);
        }
    }

    /** 400: datos inválidos detectados por reglas de negocio. */
    public static class SolicitudInvalida extends RuntimeException {
        private final transient Map<String, String> errores;

        public SolicitudInvalida(String mensaje) {
            this(mensaje, Map.of());
        }

        public SolicitudInvalida(String mensaje, Map<String, String> errores) {
            super(mensaje);
            this.errores = errores;
        }

        public Map<String, String> getErrores() {
            return errores;
        }
    }

    /** 403: autenticado pero sin derecho sobre el recurso. */
    public static class Prohibido extends RuntimeException {
        public Prohibido(String mensaje) {
            super(mensaje);
        }
    }

    /** 429: demasiados intentos fallidos de ingreso; [segundos] es lo que falta para volver a intentar. */
    public static class DemasiadosIntentos extends RuntimeException {
        private final long segundos;

        public DemasiadosIntentos(long segundos) {
            super("Demasiados intentos. Espera " + (segundos > 60 ? (segundos + 59) / 60 + " minutos" : segundos + " segundos") + " para volver a intentar.");
            this.segundos = segundos;
        }

        public long getSegundos() {
            return segundos;
        }
    }
}
