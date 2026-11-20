package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/**
 * El residente edita solo su correo, su celular y su foto. Nombre, código, DNI, carrera y ciclo los gestiona la administración.
 */
class PerfilResidenteIntegracionTest extends PruebaIntegracion {

    private int editar(String token, String cuerpo) throws Exception {
        return mvc.perform(conToken(patch("/api/v1/residentes/yo"), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse().getStatus();
    }

    @Test
    void elResidenteSoloEditaSuCorreoYSuCelular() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String token = tokenDe(ultimoUsuario);
        var antes = leer(mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andReturn().getResponse().getContentAsString());

        String celular = celularUnico();
        // Carrera, ciclo y nacimiento los gestiona la administración: si llegan, no se aplican.
        mvc.perform(conToken(patch("/api/v1/residentes/yo"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"julio@upeu.edu.pe\",\"telefono\":\"" + celular + "\",\"fechaNacimiento\":\"2004-05-17\",\"carrera\":\"Ingeniería de Sistemas\",\"ciclo\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("julio@upeu.edu.pe")).andExpect(jsonPath("$.emailVerificado").value(false))
                .andExpect(jsonPath("$.telefono").value(celular)).andExpect(jsonPath("$.ciclo").doesNotExist()).andExpect(jsonPath("$.carrera").doesNotExist())
                .andExpect(jsonPath("$.fechaNacimiento").doesNotExist());
        assertEquals(200, editar(token, "{\"email\":\"\"}"));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.telefono").value(celular))
                .andExpect(jsonPath("$.codigo").value(antes.get("codigo").asString()));
    }

    @Test
    void elCelularTiene9DigitosYNoSeRepiteYElCorreoTieneFormatoValido() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String a = tokenDe(ultimoUsuario);
        crearResidente(admin);
        String b = tokenDe(ultimoUsuario);
        String celular = celularUnico();
        assertEquals(400, editar(a, "{\"telefono\":\"12345678\"}"), "8 dígitos");
        assertEquals(400, editar(a, "{\"telefono\":\"1234567890\"}"), "10 dígitos");
        assertEquals(400, editar(a, "{\"telefono\":\"96811042a\"}"), "con letras");
        assertEquals(400, editar(a, "{\"email\":\"sin-arroba\"}"));
        assertEquals(400, editar(a, "{\"email\":\"a@b\"}"), "sin dominio");
        assertEquals(200, editar(a, "{\"telefono\":\"" + celular + "\",\"email\":\"nombre@upeu.edu.pe\"}"));
        assertEquals(409, editar(b, "{\"telefono\":\"" + celular + "\"}"), "otro usuario ya lo tiene");
        assertEquals(200, editar(a, "{\"telefono\":\"" + celular + "\"}"), "el mismo usuario puede guardarlo de nuevo");
    }

    @Test
    void elResidenteCambiaSuFotoConUnaImagenPropia() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String token = tokenDe(ultimoUsuario);
        crearResidente(admin);
        String otro = tokenDe(ultimoUsuario);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        long foto = leer(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/evidencias")
                .file(new org.springframework.mock.web.MockMultipartFile("archivo", "foto.png", "image/png", png)).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        // No se puede usar la imagen de otra persona; con la propia, la foto queda en el perfil.
        mvc.perform(conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/perfil/foto"), otro)
                .contentType(MediaType.APPLICATION_JSON).content("{\"evidenciaId\":" + foto + "}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/perfil/foto"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"evidenciaId\":" + foto + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.fotoEvidenciaId").value(foto));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.fotoEvidenciaId").value(foto));
    }

    @Test
    void elResidenteNoPuedeCambiarSuCodigoNiSuDni() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String token = tokenDe(ultimoUsuario);
        var antes = leer(mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andReturn().getResponse().getContentAsString());

        assertEquals(200, editar(token, "{\"codigo\":\"202410354\",\"dni\":\"12345678\"}"));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.codigo").value(antes.get("codigo").asString()))
                .andExpect(jsonPath("$.dni").value(antes.get("dni").asString()));

        // La administración sí los cambia, y no admite repetidos.
        String codigo = codigoUniversitario(), dni = dniAleatorio();
        mvc.perform(conToken(patch("/api/v1/residentes/" + id), admin).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\",\"dni\":\"" + dni + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value(codigo)).andExpect(jsonPath("$.dni").value(dni));
        long otro = crearResidente(admin);
        mvc.perform(conToken(patch("/api/v1/residentes/" + otro), admin).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\"}")).andExpect(status().isConflict());
        mvc.perform(conToken(patch("/api/v1/residentes/" + otro), admin).contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"" + dni + "\"}")).andExpect(status().isConflict());
        mvc.perform(conToken(patch("/api/v1/residentes/" + otro), admin).contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"123\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void validaCorreoNacimientoYCiclo() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String token = tokenDe(ultimoUsuario);
        assertEquals(400, editar(token, "{\"email\":\"no-es-correo\"}"));
        assertEquals(400, editar(token, "{\"fechaNacimiento\":\"2999-01-01\"}"), "futura");
        assertEquals(400, editar(token, "{\"ciclo\":0}"));
        assertEquals(400, editar(token, "{\"ciclo\":15}"));
        assertEquals(200, editar(token, "{\"ciclo\":14}"));
    }

    @Test
    void soloElResidenteEditaSuPerfilYLaAdministracionExigeCodigoYDni() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        assertEquals(403, editar(tokenDeRol(Rol.PRECEPTOR), "{\"ciclo\":3}"));
        assertEquals(403, editar(admin, "{\"ciclo\":3}"));
        assertEquals(401, mvc.perform(patch("/api/v1/residentes/yo").contentType(MediaType.APPLICATION_JSON).content("{\"ciclo\":3}")).andReturn().getResponse().getStatus());

        String user = "res_" + sufijo();
        String base = "{\"username\":\"" + user + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Julio Fabián Rodríguez Bazán\",";
        for (String codigo : new String[] {"ABC", "12345", "res_" + user}) {
            mvc.perform(conToken(post("/api/v1/residentes"), admin).contentType(MediaType.APPLICATION_JSON)
                    .content(base + "\"codigo\":\"" + codigo + "\",\"dni\":\"" + dniAleatorio() + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.codigo").exists());
        }
        mvc.perform(conToken(post("/api/v1/residentes"), admin).contentType(MediaType.APPLICATION_JSON).content(base + "\"codigo\":\"" + codigoUniversitario() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.dni").exists());
        mvc.perform(conToken(post("/api/v1/residentes"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(base + "\"codigo\":\"" + codigoUniversitario() + "\",\"dni\":\"" + dniAleatorio() + "\",\"fechaNacimiento\":\"2005-03-02\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fechaNacimiento").value("2005-03-02"));
    }
}
