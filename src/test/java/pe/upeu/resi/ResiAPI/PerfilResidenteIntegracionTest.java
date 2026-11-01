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

/** El residente edita su propio perfil: correo, teléfono, código universitario (9 dígitos), carrera y ciclo. */
class PerfilResidenteIntegracionTest extends PruebaIntegracion {

    private int editar(String token, String cuerpo) throws Exception {
        return mvc.perform(conToken(patch("/api/v1/residentes/yo"), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse().getStatus();
    }

    @Test
    void elResidenteEditaSusDatosYLosVeAlConsultarSuPerfil() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String token = tokenDe(ultimoUsuario);
        String codigo = codigoUniversitario();

        mvc.perform(conToken(patch("/api/v1/residentes/yo"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"julio@upeu.edu.pe\",\"telefono\":\"968110422\",\"codigo\":\"" + codigo + "\",\"carrera\":\"Ingeniería de Sistemas\",\"ciclo\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value(codigo)).andExpect(jsonPath("$.email").value("julio@upeu.edu.pe"))
                .andExpect(jsonPath("$.carrera").value("Ingeniería de Sistemas")).andExpect(jsonPath("$.ciclo").value(7));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.telefono").value("968110422")).andExpect(jsonPath("$.ciclo").value(7));

        // Editar solo una parte no toca el resto; una cadena vacía borra el dato.
        assertEquals(200, editar(token, "{\"ciclo\":8}"));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.ciclo").value(8)).andExpect(jsonPath("$.email").value("julio@upeu.edu.pe"));
        assertEquals(200, editar(token, "{\"email\":\"\",\"carrera\":\"\"}"));
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.carrera").doesNotExist())
                .andExpect(jsonPath("$.codigo").value(codigo));
    }

    @Test
    void validaCodigoCorreoYCiclo() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String token = tokenDe(ultimoUsuario);
        assertEquals(400, editar(token, "{\"codigo\":\"20241035\"}"), "8 dígitos");
        assertEquals(400, editar(token, "{\"codigo\":\"2024103545\"}"), "10 dígitos");
        assertEquals(400, editar(token, "{\"codigo\":\"20241035A\"}"), "con letras");
        assertEquals(400, editar(token, "{\"email\":\"no-es-correo\"}"));
        assertEquals(400, editar(token, "{\"ciclo\":0}"));
        assertEquals(400, editar(token, "{\"ciclo\":15}"));
        assertEquals(200, editar(token, "{\"ciclo\":14}"));
    }

    @Test
    void elCodigoUniversitarioNoSePuedeRepetir() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String tokenA = tokenDe(ultimoUsuario);
        String codigoA = leer(mvc.perform(conToken(get("/api/v1/residentes/yo"), tokenA)).andReturn().getResponse().getContentAsString()).get("codigo").asString();
        crearResidente(admin);
        String tokenB = tokenDe(ultimoUsuario);
        assertEquals(409, editar(tokenB, "{\"codigo\":\"" + codigoA + "\"}"));
        // Repetir el propio código no es un error.
        assertEquals(200, editar(tokenA, "{\"codigo\":\"" + codigoA + "\"}"));
    }

    @Test
    void soloElResidenteEditaSuPerfilYLaAdministracionExigeNueveDigitos() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        assertEquals(403, editar(tokenDeRol(Rol.PRECEPTOR), "{\"ciclo\":3}"));
        assertEquals(403, editar(admin, "{\"ciclo\":3}"));
        assertEquals(401, mvc.perform(patch("/api/v1/residentes/yo").contentType(MediaType.APPLICATION_JSON).content("{\"ciclo\":3}")).andReturn().getResponse().getStatus());

        String user = "res_" + sufijo();
        for (String codigo : new String[] {"ABC", "12345", "res_" + user}) {
            mvc.perform(conToken(post("/api/v1/residentes"), admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + user + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Julio Fabián Rodríguez Bazán\",\"codigo\":\"" + codigo + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.codigo").exists());
        }
        mvc.perform(conToken(post("/api/v1/residentes"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Julio Fabián Rodríguez Bazán\",\"codigo\":\"" + codigoUniversitario() + "\"}"))
                .andExpect(status().isCreated());
    }
}
