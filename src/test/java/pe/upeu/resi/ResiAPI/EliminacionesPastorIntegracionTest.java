package pe.upeu.resi.ResiAPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** El pastor elimina un culto creado por error, la evaluación de limpieza de una habitación y cancela reservas (con motivo); el administrador termina el semestre. */
class EliminacionesPastorIntegracionTest extends PruebaIntegracion {

    @Test
    void elPastorEliminaUnCultoSinAsistenciasYPuedeCrearOtro() throws Exception {
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        var lunes = lunesAleatorio();
        fijarLima(lunes, 6, 0);
        long id = leer(mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(delete("/api/v1/cultos/sesiones/" + id), tokenDeRol(Rol.GARITA))).andExpect(status().isForbidden());
        mvc.perform(conToken(delete("/api/v1/cultos/sesiones/" + id), pastor)).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/cultos/sesiones/" + id), pastor)).andExpect(status().isNotFound());
        mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}")).andExpect(status().is2xxSuccessful());
    }

    @Test
    void laEvaluacionDeLimpiezaSeEliminaConMotivoYLaReservaSeCancelaConMotivo() throws Exception {
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        // Sin evaluación en esa fecha: no hay nada que eliminar; el motivo es obligatorio
        mvc.perform(conToken(post("/api/v1/limpieza/habitaciones/1/eliminar"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"2020-01-06\",\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(conToken(post("/api/v1/limpieza/habitaciones/1/eliminar"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"2020-01-06\",\"motivo\":\"Error al evaluar\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(conToken(post("/api/v1/limpieza/habitaciones/1/eliminar"), tokenDeRol(Rol.ADMINISTRADOR)).contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"2020-01-06\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
        // Cancelar una reserva inexistente o sin motivo falla; solo el pastor
        mvc.perform(conToken(post("/api/v1/reservas/999999/cancelar-pastor"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Sin cupo\"}")).andExpect(status().isNotFound());
        mvc.perform(conToken(post("/api/v1/reservas/999999/cancelar-pastor"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(post("/api/v1/reservas/1/cancelar-pastor"), tokenDeRol(Rol.GARITA)).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void soloLaAdministracionTerminaElSemestre() throws Exception {
        mvc.perform(conToken(post("/api/v1/semestres/terminar"), tokenDeRol(Rol.PRECEPTOR))).andExpect(status().isForbidden());
    }
}
