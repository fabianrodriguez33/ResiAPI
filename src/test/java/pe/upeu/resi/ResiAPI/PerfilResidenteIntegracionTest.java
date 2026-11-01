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
 * El residente edita su correo, teléfono, nacimiento, carrera y ciclo. El código universitario y el DNI solo los cambia la administración.
 */
class PerfilResidenteIntegracionTest extends PruebaIntegracion {

    private int editar(String token, String cuerpo) throws Exception {
        return mvc.perform(conToken(patch("/api/v1/residentes/yo"), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse().getStatus();
    }

    @Test
    void elResidenteEditaSusDatosYLosVeAlConsultarSuPerfil() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String token = tokenDe(ultimoUsuario);

        mvc.perform(conToken(patch("/api/v1/residentes/yo"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"julio@upeu.edu.pe\",\"telefono\":\"968110422\",\"fechaNacimiento\":\"2004-05-17\",\"carrera\":\"Ingeniería de Sistemas\",\"ciclo\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("julio@upeu.edu.pe")).andExpect(jsonPath("$.emailVerificado").value(false))
                .andExpect(jsonPath("$.fechaNacimiento").value("2004-05-17")).andExpect(jsonPath("$.carrera").value("Ingeniería de Sistemas")).andExpect(jsonPath("$.ciclo").value(7));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.telefono").value("968110422")).andExpect(jsonPath("$.ciclo").value(7));

        // Editar solo una parte no toca el resto; una cadena vacía borra el dato.
        assertEquals(200, editar(token, "{\"ciclo\":8}"));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.ciclo").value(8)).andExpect(jsonPath("$.email").value("julio@upeu.edu.pe"));
        assertEquals(200, editar(token, "{\"email\":\"\",\"carrera\":\"\"}"));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.carrera").doesNotExist())
                .andExpect(jsonPath("$.fechaNacimiento").value("2004-05-17"));
    }

    @Test
    void elResidenteNoPuedeCambiarSuCodigoNiSuDni() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String token = tokenDe(ultimoUsuario);
        var antes = leer(mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andReturn().getResponse().getContentAsString());

        assertEquals(200, editar(token, "{\"codigo\":\"202410354\",\"dni\":\"12345678\",\"ciclo\":3}"));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.codigo").value(antes.get("codigo").asString()))
                .andExpect(jsonPath("$.dni").value(antes.get("dni").asString())).andExpect(jsonPath("$.ciclo").value(3));

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
        assertEquals(400, editar(token, "{\"fechaNacimiento\":\"1850-01-01\"}"), "demasiado antigua");
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
