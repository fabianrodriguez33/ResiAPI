package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** El pastor anula permisos aprobados, y marca (o quita) los días sin cultos. */
class AnulacionYCalendarioIntegracionTest extends PruebaIntegracion {

    private record Escenario(String admin, String pastor, String garita, String residente, long residenteId, String qr) {
    }

    private Escenario escenario() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String usuario = ultimoUsuario;
        mvc.perform(conToken(patch("/api/v1/residentes/" + id), admin).contentType(MediaType.APPLICATION_JSON).content("{\"puedeSolicitarSolo\":true}")).andExpect(status().isOk());
        return new Escenario(admin, tokenDeRol(Rol.PRECEPTOR), tokenDeRol(Rol.GARITA), tokenDe(usuario), id, qrDe(usuario));
    }

    private long permisoAprobado(Escenario e, Instant salida) throws Exception {
        var res = mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"LARGO\",\"motivo\":\"Visita\",\"destino\":\"Casa\",\"salida\":\"" + salida + "\",\"retorno\":\"" + salida.plus(Duration.ofHours(4)) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("APROBADO")).andReturn().getResponse();
        return leer(res.getContentAsString()).get("id").asLong();
    }

    private String verificacion(Escenario e) throws Exception {
        return leer(mvc.perform(conToken(post("/api/v1/garita/verificaciones"), e.garita()).contentType(MediaType.APPLICATION_JSON).content("{\"qr\":\"" + e.qr() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("resultado").asString();
    }

    private int anular(String token, long id, String motivo) throws Exception {
        return mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/anular"), token).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + motivo + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void elPastorAnulaUnPermisoAprobadoYGaritaYaNoLoDejaSalir() throws Exception {
        var e = escenario();
        fijarLima(lunesAleatorio(), 10, 0);
        Instant salida = reloj.instant().plus(Duration.ofHours(1));
        long id = permisoAprobado(e, salida);
        reloj.fijar(salida.plusSeconds(60));
        assertEquals("AUTORIZADO_SALIDA", verificacion(e));

        reloj.fijar(salida.minus(Duration.ofMinutes(30)));
        assertEquals(403, anular(e.residente(), id, "No quiero"), "el residente no anula");
        assertEquals(403, anular(e.garita(), id, "No"));
        assertEquals(400, anular(e.pastor(), id, ""), "el motivo es obligatorio");
        assertEquals(200, anular(e.pastor(), id, "No cumplió con la limpieza"));
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.residente())).andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.comentarioDecision").value("No cumplió con la limpieza"));
        assertEquals(409, anular(e.pastor(), id, "otra vez"), "ya no está aprobado");

        reloj.fijar(salida.plusSeconds(60));
        assertEquals("SIN_PERMISO", verificacion(e));
    }

    @Test
    void noSeAnulaUnPermisoConElQueElResidenteYaSalio() throws Exception {
        var e = escenario();
        fijarLima(lunesAleatorio(), 10, 0);
        Instant salida = reloj.instant().plus(Duration.ofHours(1));
        long id = permisoAprobado(e, salida);
        reloj.fijar(salida.plusSeconds(60));
        mvc.perform(conToken(post("/api/v1/garita/movimientos"), e.garita()).contentType(MediaType.APPLICATION_JSON).content("{\"qr\":\"" + e.qr() + "\",\"tipo\":\"SALIDA\"}"))
                .andExpect(status().isCreated());
        assertEquals(409, anular(e.pastor(), id, "Tarde"));
    }

    @Test
    void elPastorYLaAdministracionMarcanDiasSinCultoYTodosLosVen() throws Exception {
        String pastor = tokenDeRol(Rol.PRECEPTOR), admin = tokenDeRol(Rol.ADMINISTRADOR), residente = tokenDe(crearResidenteYUsuario(admin));
        LocalDate lunes = lunesAleatorio();
        String feriado = lunes.plusDays(2).toString(), sabado = lunes.plusDays(5).toString();

        assertEquals(201, marcar(pastor, feriado, "Feriado nacional"));
        assertEquals(409, marcar(admin, feriado, "Repetido"));
        assertEquals(400, marcar(admin, sabado, "Fin de semana"));
        assertEquals(400, marcar(pastor, lunes.plusDays(1).toString(), ""));
        assertEquals(403, marcar(residente, lunes.plusDays(3).toString(), "No debería"));

        mvc.perform(conToken(get("/api/v1/calendario/dias-sin-culto").param("desde", lunes.toString()).param("hasta", lunes.plusDays(6).toString()), residente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].fecha").value(feriado)).andExpect(jsonPath("$[0].motivo").value("Feriado nacional"));

        // Ese día no se puede abrir un culto.
        fijarLima(lunes.plusDays(2), 7, 0);
        mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}")).andExpect(status().isConflict());

        assertEquals(403, quitar(residente, feriado));
        assertEquals(204, quitar(pastor, feriado));
        assertEquals(404, quitar(pastor, feriado));
        mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}")).andExpect(status().isOk());
    }

    private String crearResidenteYUsuario(String admin) throws Exception {
        crearResidente(admin);
        return ultimoUsuario;
    }

    private int marcar(String token, String fecha, String motivo) throws Exception {
        return mvc.perform(conToken(post("/api/v1/calendario/dias-sin-culto"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + fecha + "\",\"motivo\":\"" + motivo + "\"}")).andReturn().getResponse().getStatus();
    }

    private int quitar(String token, String fecha) throws Exception {
        return mvc.perform(conToken(delete("/api/v1/calendario/dias-sin-culto/" + fecha), token)).andReturn().getResponse().getStatus();
    }
}
