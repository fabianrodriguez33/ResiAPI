package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Limpieza por habitación: basura y olores comunes; ropero, cama, escritorio y piso personales. */
class LimpiezaHabitacionIntegracionTest extends PruebaIntegracion {

    private void asignar(String admin, long residente, long habitacion) throws Exception {
        mvc.perform(conToken(post("/api/v1/residentes/" + residente + "/asignacion"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"habitacionId\":" + habitacion + "}")).andExpect(status().isOk());
    }

    private static String persona(long id, int ropero, int cama, int escritorio, int piso) {
        return "{\"residenteId\":" + id + ",\"ropero\":" + ropero + ",\"cama\":" + cama + ",\"escritorio\":" + escritorio + ",\"piso\":" + piso + "}";
    }

    private static String cuerpo(LocalDate fecha, int basura, int olores, String... personas) {
        return "{\"fecha\":\"" + fecha + "\",\"basura\":" + basura + ",\"olores\":" + olores + ",\"residentes\":[" + String.join(",", personas) + "]}";
    }

    private int enviar(String token, long habitacion, String cuerpo) throws Exception {
        return mvc.perform(conToken(post("/api/v1/limpieza/habitaciones/" + habitacion + "/evaluaciones"), token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse().getStatus();
    }

    @Test
    void laHabitacionSeEvaluaConBasuraYOloresComunesYElRestoPersonal() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), preceptor = tokenDeRol(Rol.PRECEPTOR);
        long hab = crearHabitacion(admin, 2);
        long r1 = crearResidente(admin), r2 = crearResidente(admin);
        asignar(admin, r1, hab);
        asignar(admin, r2, hab);
        LocalDate fecha = lunesAleatorio();
        fijarLima(fecha, 10, 0);

        // La lista de habitaciones trae los ocupantes con su cama, sin evaluar.
        var lista = leer(mvc.perform(conToken(get("/api/v1/limpieza/habitaciones").param("fecha", fecha.toString()), preceptor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var propia = lista.valueStream().filter(h -> h.get("habitacionId").asLong() == hab).findFirst().orElseThrow();
        assertEquals(2, propia.get("residentes").size());
        assertEquals("A", propia.get("residentes").get(0).get("cama").asString());
        assertEquals(0, propia.get("evaluados").asLong());

        var resp = mvc.perform(conToken(post("/api/v1/limpieza/habitaciones/" + hab + "/evaluaciones"), preceptor).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(fecha, 2, 1, persona(r1, 5, 3, 3, 3), persona(r2, 4, 2, 1, 0))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var evaluaciones = leer(resp);
        assertEquals(2, evaluaciones.size());
        assertEquals(5 + 3 + 3 + 2 + 1 + 3, evaluaciones.get(0).get("total").asInt());
        assertEquals(4 + 2 + 1 + 2 + 1 + 0, evaluaciones.get(1).get("total").asInt());
        for (var e : evaluaciones) {
            assertEquals(2, e.get("puntajes").get("BASURA").asInt(), "basura común a la habitación");
            assertEquals(1, e.get("puntajes").get("OLORES").asInt(), "olores común a la habitación");
        }

        var despues = leer(mvc.perform(conToken(get("/api/v1/limpieza/habitaciones").param("fecha", fecha.toString()), preceptor))
                .andReturn().getResponse().getContentAsString());
        var evaluada = despues.valueStream().filter(h -> h.get("habitacionId").asLong() == hab).findFirst().orElseThrow();
        assertEquals(2, evaluada.get("evaluados").asLong());

        // No se puede evaluar dos veces el mismo día.
        assertEquals(409, enviar(preceptor, hab, cuerpo(fecha, 2, 1, persona(r1, 5, 3, 3, 3))));
    }

    @Test
    void validaMaximosPertenenciaYFecha() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), preceptor = tokenDeRol(Rol.PRECEPTOR);
        long hab = crearHabitacion(admin, 2), otra = crearHabitacion(admin, 2);
        long r1 = crearResidente(admin), ajeno = crearResidente(admin);
        asignar(admin, r1, hab);
        asignar(admin, ajeno, otra);
        LocalDate fecha = lunesAleatorio();
        fijarLima(fecha, 10, 0);

        assertEquals(400, enviar(preceptor, hab, cuerpo(fecha, 4, 1, persona(r1, 5, 3, 3, 3))), "basura máximo 3");
        assertEquals(400, enviar(preceptor, hab, cuerpo(fecha, 3, 1, persona(r1, 6, 3, 3, 3))), "ropero máximo 5");
        assertEquals(400, enviar(preceptor, hab, cuerpo(fecha, 3, 1, persona(r1, 5, 3, 3, -1))), "negativos");
        assertEquals(400, enviar(preceptor, hab, cuerpo(fecha, 3, 1, persona(ajeno, 5, 3, 3, 3))), "residente de otra habitación");
        assertEquals(400, enviar(preceptor, hab, cuerpo(fecha, 3, 1, persona(r1, 5, 3, 3, 3), persona(r1, 5, 3, 3, 3))), "residente repetido");
        assertEquals(400, enviar(preceptor, hab, cuerpo(fecha.plusDays(1), 3, 1, persona(r1, 5, 3, 3, 3))), "fecha futura");
        assertEquals(400, enviar(preceptor, hab, "{\"fecha\":\"" + fecha + "\",\"basura\":3,\"olores\":1,\"residentes\":[]}"), "sin residentes");
        // Nada se guardó por los intentos fallidos.
        assertEquals(201, enviar(preceptor, hab, cuerpo(fecha, 3, 1, persona(r1, 5, 3, 3, 3))));
    }

    @Test
    void siSeEvaluaPorPartesBasuraYOloresDebenCoincidir() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), preceptor = tokenDeRol(Rol.PRECEPTOR);
        long hab = crearHabitacion(admin, 2);
        long r1 = crearResidente(admin), r2 = crearResidente(admin);
        asignar(admin, r1, hab);
        asignar(admin, r2, hab);
        LocalDate fecha = lunesAleatorio();
        fijarLima(fecha, 10, 0);

        assertEquals(201, enviar(preceptor, hab, cuerpo(fecha, 2, 1, persona(r1, 5, 3, 3, 3))));
        assertEquals(409, enviar(preceptor, hab, cuerpo(fecha, 3, 1, persona(r2, 5, 3, 3, 3))), "basura distinta para la misma habitación");
        assertEquals(201, enviar(preceptor, hab, cuerpo(fecha, 2, 1, persona(r2, 5, 3, 3, 3))));
    }

    @Test
    void soloElPreceptorEvaluaYUnResidenteNoVeLaLista() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), residente = tokenDeRol(Rol.RESIDENTE), garita = tokenDeRol(Rol.GARITA);
        long hab = crearHabitacion(admin, 2);
        long r1 = crearResidente(admin);
        asignar(admin, r1, hab);
        LocalDate fecha = lunesAleatorio();
        assertEquals(403, enviar(residente, hab, cuerpo(fecha, 3, 1, persona(r1, 5, 3, 3, 3))));
        assertEquals(403, enviar(admin, hab, cuerpo(fecha, 3, 1, persona(r1, 5, 3, 3, 3))));
        mvc.perform(conToken(get("/api/v1/limpieza/habitaciones"), residente)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/limpieza/habitaciones"), garita)).andExpect(status().isForbidden());
        assertTrue(true);
    }
}
