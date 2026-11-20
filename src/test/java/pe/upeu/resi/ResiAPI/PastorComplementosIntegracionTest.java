package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Lo que el pastor o monitor hace además de revisar: crear y cerrar reservas, exportar, código manual y su foto. */
class PastorComplementosIntegracionTest extends PruebaIntegracion {

    private String cuerpoReserva(long residenteId, String tipo, LocalDate fecha) {
        return "{\"residenteId\":" + residenteId + ",\"tipo\":\"" + tipo + "\",\"fecha\":\"" + fecha + "\",\"motivo\":\"Clases\",\"curso\":\"Cálculo\"}";
    }

    @Test
    void elPastorCreaReservasConSusHorariosLasCierraYLasExporta() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        long idA = crearResidente(admin);
        long idB = crearResidente(admin);
        LocalDate lunes = lunesAleatorio();

        // Almuerzo: hasta la 1 p. m.; pasada la hora ya no se abre ese día.
        fijarLima(lunes, 12, 30);
        mvc.perform(conToken(post("/api/v1/reservas/pastor"), pastor).contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(idA, "ALMUERZO", lunes)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("RESERVADA"));
        mvc.perform(conToken(post("/api/v1/reservas/pastor"), pastor).contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(idA, "ALMUERZO", lunes)))
                .andExpect(status().isConflict()); // ya la tiene
        fijarLima(lunes, 13, 1);
        mvc.perform(conToken(post("/api/v1/reservas/pastor"), pastor).contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(idB, "ALMUERZO", lunes)))
                .andExpect(status().isConflict());

        // Cena: hasta las 6 p. m.
        fijarLima(lunes, 17, 59);
        mvc.perform(conToken(post("/api/v1/reservas/pastor"), pastor).contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(idB, "CENA_TAPER", lunes)))
                .andExpect(status().isCreated());
        fijarLima(lunes, 18, 1);
        mvc.perform(conToken(post("/api/v1/reservas/pastor"), pastor).contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(idA, "CENA_TAPER", lunes)))
                .andExpect(status().isConflict());
        // Solo el pastor crea reservas de otros.
        mvc.perform(conToken(post("/api/v1/reservas/pastor"), tokenDe(ultimoUsuario)).contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(idA, "ALMUERZO", lunes)))
                .andExpect(status().isForbidden());

        // Cerrar el almuerzo: ya no entran reservas y la lista se exporta con la aprobada.
        mvc.perform(conToken(post("/api/v1/reservas/cerrar"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"" + lunes + "\",\"grupo\":\"ALMUERZO\"}"))
                .andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/reservas/cerrar"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"" + lunes + "\",\"grupo\":\"ALMUERZO\"}"))
                .andExpect(status().isConflict());
        mvc.perform(conToken(get("/api/v1/reservas/cierres").param("fecha", lunes.toString()), pastor)).andExpect(jsonPath("$[0]").value("ALMUERZO"));
        String csv = mvc.perform(conToken(get("/api/v1/reservas/exportar").param("fecha", lunes.toString()).param("grupo", "ALMUERZO"), pastor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Código;Nombre;Comida;Motivo;Curso") && csv.contains("Cálculo"), csv);
    }

    @Test
    void elCodigoManualRegistraLaAsistenciaYSeExportaLaLista() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        long id = crearResidente(admin);
        String codigo = leer(mvc.perform(conToken(get("/api/v1/residentes/" + id), admin)).andReturn().getResponse().getContentAsString()).get("codigo").asString();
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 7, 0);
        long sesion = leer(mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        String url = "/api/v1/cultos/sesiones/" + sesion + "/asistencias";
        mvc.perform(conToken(post(url), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"000000000\"}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(post(url), pastor).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(post(url), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(codigo));
        mvc.perform(conToken(post(url), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\"}")).andExpect(status().isConflict());
        String csv = mvc.perform(conToken(get("/api/v1/cultos/sesiones/" + sesion + "/exportar"), pastor)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains(codigo), csv);
    }

    @Test
    void elPastorTambienTieneFotoDePerfil() throws Exception {
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        long foto = leer(mvc.perform(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "foto.png", "image/png", png))
                .header("Authorization", "Bearer " + pastor)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(put("/api/v1/perfil/foto"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"evidenciaId\":" + foto + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotoEvidenciaId").value(foto));
        mvc.perform(conToken(get("/api/v1/evidencias/" + foto), pastor)).andExpect(status().isOk());
    }
}
