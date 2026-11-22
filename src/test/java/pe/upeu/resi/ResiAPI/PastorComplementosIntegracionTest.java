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

/** Lo que el pastor o monitor hace además de revisar: abrir y cerrar reservas, exportar, código manual y su foto. */
class PastorComplementosIntegracionTest extends PruebaIntegracion {

    @org.springframework.beans.factory.annotation.Autowired
    pe.upeu.resi.ResiAPI.repository.ResidenteRepository residentes;


    @Test
    void elPastorAbreApruebaCierraYExportaSoloNombreYCodigo() throws Exception {
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        long foto = leer(mvc.perform(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "foto.png", "image/png", png))
                .header("Authorization", "Bearer " + residente)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        String grupo = "{\"fecha\":\"" + lunes + "\",\"grupo\":\"ALMUERZO\"}";
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON).content(grupo)).andExpect(status().isOk());
        long reserva = leer(mvc.perform(conToken(post("/api/v1/reservas"), residente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"ALMUERZO\",\"fecha\":\"" + lunes + "\",\"motivo\":\"Clases\",\"curso\":\"Cálculo\",\"evidenciaId\":" + foto + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/reservas/" + reserva + "/decision"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isOk());
        // Solo el pastor abre, cierra y exporta
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), tokenDeRol(Rol.GARITA)).contentType(MediaType.APPLICATION_JSON).content(grupo)).andExpect(status().isForbidden());

        // Cerrar: ya no entran reservas, no se vuelve a cerrar ni a abrir ese día, y la lista se exporta con la aprobada.
        mvc.perform(conToken(post("/api/v1/reservas/cerrar"), pastor).contentType(MediaType.APPLICATION_JSON).content(grupo)).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/reservas/cerrar"), pastor).contentType(MediaType.APPLICATION_JSON).content(grupo)).andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON).content(grupo)).andExpect(status().isConflict());
        mvc.perform(conToken(get("/api/v1/reservas/cierres").param("fecha", lunes.toString()), pastor)).andExpect(jsonPath("$[0]").value("ALMUERZO"));
        String csv = mvc.perform(conToken(get("/api/v1/reservas/exportar").param("fecha", lunes.toString()).param("grupo", "ALMUERZO"), pastor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String codigo = residentes.findById(id).orElseThrow().getCodigo();
        assertTrue(csv.contains("Código;Nombre") && csv.contains(codigo) && !csv.contains("Motivo") && !csv.contains("Cálculo"), csv);
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

    @Test
    void laReservaPuedeLlevarUnaFotoPropia() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String a = tokenDe(ultimoUsuario);
        crearResidente(admin);
        String b = tokenDe(ultimoUsuario);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        long foto = leer(mvc.perform(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "foto.png", "image/png", png))
                .header("Authorization", "Bearer " + a)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), tokenDeRol(Rol.PRECEPTOR)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + lunes + "\",\"grupo\":\"ALMUERZO\"}")).andExpect(status().isOk());
        String cuerpo = "{\"tipo\":\"ALMUERZO\",\"fecha\":\"" + lunes + "\",\"motivo\":\"Clases\",\"curso\":\"Cálculo\",\"evidenciaId\":" + foto + "}";
        // La foto de otra persona no se puede usar; la propia queda en la reserva.
        mvc.perform(conToken(post("/api/v1/reservas"), b).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        mvc.perform(conToken(post("/api/v1/reservas"), a).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenciaId").value(foto));
    }
}
