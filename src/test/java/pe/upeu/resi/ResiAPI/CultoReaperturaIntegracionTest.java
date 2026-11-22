package pe.upeu.resi.ResiAPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** El pastor ve los cultos de cualquier fecha y puede abrir de nuevo, el mismo día, uno que cerró. */
class CultoReaperturaIntegracionTest extends PruebaIntegracion {

    @Test
    void sePuedeReabrirUnCultoCerradoSoloElMismoDia() throws Exception {
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 7, 0);
        long id = leer(mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + id + "/reabrir"), pastor)).andExpect(status().isConflict()); // ya está abierto
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + id + "/cerrar"), pastor)).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/cultos/sesiones").param("fecha", lunes.toString()), pastor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("CERRADA"));
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + id + "/reabrir"), tokenDeRol(Rol.GARITA))).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + id + "/reabrir"), pastor)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ABIERTA"));
        // Otro día ya no se reabre.
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + id + "/cerrar"), pastor)).andExpect(status().isOk());
        fijarLima(lunes.plusDays(1), 7, 0);
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + id + "/reabrir"), pastor)).andExpect(status().isConflict());
    }
}
