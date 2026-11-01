package pe.upeu.resi.ResiAPI.service;

/** Envía mensajes de texto. */
public interface EnviadorSms {

    void enviar(String telefono, String texto);
}
