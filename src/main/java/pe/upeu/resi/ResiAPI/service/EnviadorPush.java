package pe.upeu.resi.ResiAPI.service;

/** Envía notificaciones push a un teléfono registrado. */
public interface EnviadorPush {

    /** @return false si el token ya no sirve y debe olvidarse. */
    boolean notificar(String tokenDispositivo, String titulo, String mensaje);
}
