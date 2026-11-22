package pe.upeu.resi.ResiAPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** El ranking sale de los registros originales: cultos asistidos y promedio de limpieza del ciclo. */
class RankingIntegracionTest extends PruebaIntegracion {

    @Test
    void elPuestoSigueALosCultosYALaLimpiezaDelCiclo() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        long idA = crearResidente(admin);
        String qrA = qrDe(ultimoUsuario);
        String tokenA = tokenDe(ultimoUsuario);
        long idB = crearResidente(admin);
        String tokenB = tokenDe(ultimoUsuario);

        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 7, 0);
        long sesion = leer(mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/asistencias"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qrA + "\"}")).andExpect(status().isCreated());
        // A rinde 18/20 y B 12/20 en limpieza.
        String bueno = "\"puntajes\":{\"ROPERO\":5,\"CAMA\":3,\"ESCRITORIO\":3,\"BASURA\":3,\"OLORES\":2,\"PISO\":2}";
        String regular = "\"puntajes\":{\"ROPERO\":3,\"CAMA\":2,\"ESCRITORIO\":2,\"BASURA\":2,\"OLORES\":2,\"PISO\":1}";
        for (var par : new Object[][] {{idA, bueno}, {idB, regular}}) {
            mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), pastor).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"residenteId\":" + par[0] + ",\"fecha\":\"" + lunes + "\"," + par[1] + "}")).andExpect(status().isCreated());
        }

        // Los puestos se comparan con todos los residentes activos de la base de pruebas, así que solo se afirma el orden entre A y B.
        var a = leer(mvc.perform(conToken(get("/api/v1/residentes/yo/ranking"), tokenA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cultos.valor").value(1.0)).andExpect(jsonPath("$.limpieza.valor").value(18.0)).andReturn().getResponse().getContentAsString());
        var b = leer(mvc.perform(conToken(get("/api/v1/residentes/yo/ranking"), tokenB)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cultos.valor").value(0.0)).andExpect(jsonPath("$.limpieza.valor").value(12.0)).andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(a.get("cultos").get("posicion").asInt() < b.get("cultos").get("posicion").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(a.get("limpieza").get("posicion").asInt() < b.get("limpieza").get("posicion").asInt());
        // Solo el residente consulta su ranking.
        mvc.perform(conToken(get("/api/v1/residentes/yo/ranking"), pastor)).andExpect(status().isForbidden());
    }

    @Test
    void elRankingTambienSeCalculaParaUnaSemanaConcreta() throws Exception {
        crearResidente(tokenDeRol(Rol.ADMINISTRADOR));
        String residente = tokenDe(ultimoUsuario);
        var semana = lunesAleatorio();
        mvc.perform(conToken(get("/api/v1/residentes/yo/ranking").param("desde", semana.toString()).param("hasta", semana.plusDays(6).toString()), residente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.desde").value(semana.toString())).andExpect(jsonPath("$.hasta").value(semana.plusDays(6).toString()));
        mvc.perform(conToken(get("/api/v1/residentes/yo/ranking").param("desde", semana.toString()).param("hasta", semana.minusDays(1).toString()), residente))
                .andExpect(status().isBadRequest());
    }
}
