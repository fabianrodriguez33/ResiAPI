package pe.upeu.resi.ResiAPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Semestres (2026-2, 2027-1...) y liberar la cama de un residente. */
class SemestreIntegracionTest extends PruebaIntegracion {

    @Test
    void elAdministradorCreaUnSemestreNuevoYLasHabitacionesQuedanVacias() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        long hab = -1;
        for (var h : leer(mvc.perform(conToken(get("/api/v1/habitaciones"), admin)).andReturn().getResponse().getContentAsString())) {
            if (h.get("disponibles").asLong() > 0) { hab = h.get("id").asLong(); break; }
        }
        // Liberar una cama: se asigna, se libera, y liberar de nuevo falla
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/asignacion"), admin).contentType(MediaType.APPLICATION_JSON).content("{\"habitacionId\":" + hab + "}")).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/liberar-habitacion"), admin)).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/liberar-habitacion"), admin)).andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/liberar-habitacion"), tokenDeRol(Rol.PRECEPTOR))).andExpect(status().isForbidden());

        // Todos ven el semestre actual; solo la administración crea
        String residente = tokenDe(ultimoUsuario);
        mvc.perform(conToken(get("/api/v1/semestres/actual"), residente)).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        String nombre = "2" + String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(100, 999)) + "-1";
        String cuerpo = "{\"nombre\":\"" + nombre + "\",\"inicio\":\"2027-03-15\",\"fin\":\"2027-07-10\"}";
        mvc.perform(conToken(post("/api/v1/semestres"), residente).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/semestres"), admin).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"mal\",\"inicio\":\"2027-03-15\",\"fin\":\"2027-03-01\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.nombre").exists()).andExpect(jsonPath("$.validationErrors.fin").exists());
        // Al crearlo, el semestre nuevo queda activo y las asignaciones vigentes se cierran
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/asignacion"), admin).contentType(MediaType.APPLICATION_JSON).content("{\"habitacionId\":" + hab + "}")).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/semestres"), admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value(nombre));
        mvc.perform(conToken(get("/api/v1/semestres/actual"), admin)).andExpect(jsonPath("$.nombre").value(nombre));
        mvc.perform(conToken(get("/api/v1/residentes/" + id), admin)).andExpect(jsonPath("$.habitacion").doesNotExist());
        mvc.perform(conToken(post("/api/v1/semestres"), admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
    }
}
