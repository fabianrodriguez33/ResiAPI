package pe.upeu.resi.ResiAPI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnviadorCorreoBrevoApiTest {

    @Test
    void escapaComillasSaltosYBarras() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", EnviadorCorreoBrevoApi.json("a\"b\\c\nd"));
    }

    @Test
    void conservaAcentos() {
        assertEquals("\"código\"", EnviadorCorreoBrevoApi.json("código"));
    }
}
