package pe.upeu.resi.ResiAPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** 5 fallos bloquean 1 minuto; los 5 siguientes, 10 minutos; un ingreso correcto reinicia la cuenta. */
class LimiteLoginIntegracionTest extends PruebaIntegracion {

    private void intentar(String usuario, String clave, int esperado) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + usuario + "\",\"password\":\"" + clave + "\"}")).andExpect(status().is(esperado));
    }

    private void fallar(String usuario, int veces) throws Exception {
        for (int i = 0; i < veces; i++) intentar(usuario, "incorrecta", 401);
    }

    @Test
    void cincoFallosBloquean1MinutoYLosSiguientesCincoBloquean10() throws Exception {
        String u = crearPersonal(Rol.PRECEPTOR);
        var inicio = Instant.parse("2031-03-03T15:00:00Z");
        reloj.fijar(inicio);

        fallar(u, 5);
        // Bloqueado: ni siquiera la clave correcta entra y responde 429 con el tiempo de espera.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + u + "\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));

        reloj.fijar(inicio.plusSeconds(59));
        intentar(u, CLAVE, 429);
        reloj.fijar(inicio.plusSeconds(61));
        fallar(u, 4); // 4 de los 5 siguientes
        intentar(u, "incorrecta", 401); // el quinto activa los 10 minutos
        var segundoBloqueo = inicio.plusSeconds(61);
        reloj.fijar(segundoBloqueo.plusSeconds(9 * 60));
        intentar(u, CLAVE, 429);
        reloj.fijar(segundoBloqueo.plusSeconds(10 * 60 + 1));
        intentar(u, CLAVE, 200);
    }

    @Test
    void unIngresoCorrectoReiniciaLaCuentaYLosDemasUsuariosNoSeAfectan() throws Exception {
        String u = crearPersonal(Rol.GARITA), otro = crearPersonal(Rol.GARITA);
        reloj.fijar(Instant.parse("2031-03-03T15:00:00Z"));
        fallar(u, 4);
        intentar(u, CLAVE, 200);
        fallar(u, 4);
        intentar(u, CLAVE, 200); // no acumuló los 8 fallos

        fallar(u, 5);
        intentar(u, CLAVE, 429);
        intentar(otro, CLAVE, 200);
    }

    @Test
    void tambienSeLimitanLosUsuariosInexistentes() throws Exception {
        String fantasma = "nadie_" + sufijo();
        reloj.fijar(Instant.parse("2031-03-03T15:00:00Z"));
        fallar(fantasma, 5);
        intentar(fantasma, "incorrecta", 429);
    }
}
