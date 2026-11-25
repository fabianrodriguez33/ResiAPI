package pe.upeu.resi.ResiAPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Autorización por tipo (salta al apoderado) y fecha estricta (nada de fechas u horas ya pasadas). */
class AutorizacionPermisoIntegracionTest extends PruebaIntegracion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private ResultActions pedir(String token, String tipo, Instant salida, Instant retorno) throws Exception {
        String extra = tipo.equals("PERNOCTE") ? ",\"responsableNombre\":\"Tía\",\"responsableCelular\":\"999888777\"" : "";
        return mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"" + tipo + "\",\"motivo\":\"Trámite\",\"destino\":\"Lima\",\"salida\":\"" + salida + "\",\"retorno\":\"" + retorno + "\"" + extra + "}"));
    }

    @Test
    void conAutorizacionVigenteElPermisoVaDirectoAlPastorYSinEllaPrimeroAlApoderado() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String res = tokenDe(ultimoUsuario);
        // Sin autorización ni apoderado vinculado: no se puede (necesita apoderado)
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 9, 0);
        Instant salida = lunes.atTime(10, 0).atZone(LIMA).toInstant(), retorno = lunes.atTime(14, 0).atZone(LIMA).toInstant();
        pedir(res, "LARGO", salida, retorno).andExpect(status().isConflict());

        // La administración registra la hoja del tipo LARGO: ahora va directo al pastor
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/autorizaciones"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"LARGO\",\"referencia\":\"Hoja 123\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.vigente").value(true));
        pedir(res, "LARGO", salida, retorno).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE_PRECEPTOR"));
        // La autorización de un tipo no habilita otro
        Instant retornoDia = lunes.plusDays(1).atTime(10, 0).atZone(LIMA).toInstant();
        pedir(res, "PERNOCTE", salida, retornoDia).andExpect(status().isConflict());

        // Solo la administración la gestiona; sin referencia no se registra; revocar exige motivo y quita la autonomía
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/autorizaciones"), tokenDeRol(Rol.PRECEPTOR)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CORTO\",\"referencia\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/autorizaciones"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CORTO\",\"referencia\":\" \"}")).andExpect(status().isBadRequest());
        var lista = leer(mvc.perform(conToken(get("/api/v1/residentes/" + id + "/autorizaciones"), admin)).andReturn().getResponse().getContentAsString());
        long aut = lista.get(0).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/autorizaciones/" + aut + "/revocar"), admin).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(conToken(post("/api/v1/autorizaciones/" + aut + "/revocar"), admin).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Los padres la retiraron\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revocada").value(true)).andExpect(jsonPath("$.vigente").value(false));
        Instant salida2 = lunes.atTime(15, 0).atZone(LIMA).toInstant(), retorno2 = lunes.atTime(18, 0).atZone(LIMA).toInstant();
        pedir(res, "LARGO", salida2, retorno2).andExpect(status().isConflict());
    }

    @Test
    void laFechaEsEstrictaNoSePideUnDiaAnteriorNiUnaHoraQueYaPaso() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String res = tokenDe(ultimoUsuario);
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/autorizaciones"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"LARGO\",\"referencia\":\"Hoja 9\"}")).andExpect(status().isCreated());
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 11, 0);
        // Ayer: no
        pedir(res, "LARGO", lunes.minusDays(1).atTime(15, 0).atZone(LIMA).toInstant(), lunes.minusDays(1).atTime(20, 0).atZone(LIMA).toInstant())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.salida").exists());
        // Hoy, pero a las 10 estando las 11: no
        pedir(res, "LARGO", lunes.atTime(10, 0).atZone(LIMA).toInstant(), lunes.atTime(14, 0).atZone(LIMA).toInstant())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.salida").exists());
        // Hoy a las 12: sí
        pedir(res, "LARGO", lunes.atTime(12, 0).atZone(LIMA).toInstant(), lunes.atTime(16, 0).atZone(LIMA).toInstant()).andExpect(status().isCreated());
    }
}
