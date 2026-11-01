package pe.upeu.resi.ResiAPI.service;

/** Envía el código de verificación de correo. */
public interface EnviadorCorreo {

    void enviarCodigo(String destino, String codigo);
}
