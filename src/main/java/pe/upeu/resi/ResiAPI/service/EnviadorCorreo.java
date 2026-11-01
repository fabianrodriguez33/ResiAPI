package pe.upeu.resi.ResiAPI.service;

/** Envía correos de la aplicación: el código de verificación y las notificaciones. */
public interface EnviadorCorreo {

    void enviarCodigo(String destino, String codigo);

    /** Notificación por correo. Si el destino no está autorizado o no hay SMTP, no hace nada (no es un error). */
    default void enviar(String destino, String asunto, String texto) {
    }
}
