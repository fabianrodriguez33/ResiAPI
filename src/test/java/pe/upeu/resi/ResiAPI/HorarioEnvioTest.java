package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import pe.upeu.resi.ResiAPI.service.HorarioEnvio;

class HorarioEnvioTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private boolean abierto(int dia, int hora, int minuto) {
        // 2026-11-01 es domingo
        return HorarioEnvio.abierto(LocalDateTime.of(2026, 11, 1 + dia, hora, minuto).atZone(LIMA).toInstant(), LIMA);
    }

    @Test
    void domingoAJuevesDesdeLas530HastaLas2200() {
        for (int dia = 0; dia <= 4 - 1; dia++) { // domingo, lunes, martes, miércoles
            assertEquals(false, abierto(dia, 5, 29));
            assertEquals(true, abierto(dia, 5, 30));
            assertEquals(true, abierto(dia, 22, 0));
            assertEquals(false, abierto(dia, 22, 1));
        }
        assertEquals(true, abierto(4, 21, 59)); // jueves
        assertEquals(false, abierto(4, 23, 0));
    }

    @Test
    void viernesHastaLas1400() {
        assertEquals(false, abierto(5, 5, 29));
        assertEquals(true, abierto(5, 5, 30));
        assertEquals(true, abierto(5, 14, 0));
        assertEquals(false, abierto(5, 14, 1));
        assertEquals(false, abierto(5, 20, 0));
    }

    @Test
    void sabadoSoloDe1830A2200() {
        assertEquals(false, abierto(6, 6, 0));
        assertEquals(false, abierto(6, 18, 29));
        assertEquals(true, abierto(6, 18, 30));
        assertEquals(true, abierto(6, 22, 0));
        assertEquals(false, abierto(6, 22, 1));
    }
}
