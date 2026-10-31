package pe.upeu.resi.ResiAPI;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.service.UsuarioService;

/** Base de las pruebas de integración: PostgreSQL real, MockMvc y utilidades para crear usuarios y tokens. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PruebaIntegracion {

    protected static final String CLAVE = "Clave-de-prueba-123";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected UsuarioService usuarioService;

    protected String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Crea un usuario de personal y devuelve su nombre de usuario. */
    protected String crearPersonal(Rol rol) {
        String username = rol.name().toLowerCase() + "_" + sufijo();
        usuarioService.crear(username, CLAVE, "Usuario " + username, rol);
        return username;
    }

    protected String tokenDe(String username) throws Exception {
        var res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + CLAVE + "\"}")).andReturn().getResponse();
        if (res.getStatus() != 200) {
            throw new IllegalStateException("Login falló para " + username + ": " + res.getStatus());
        }
        return json.readTree(res.getContentAsString()).get("token").asString();
    }

    protected String tokenDeRol(Rol rol) throws Exception {
        return tokenDe(crearPersonal(rol));
    }

    protected MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    protected JsonNode leer(String cuerpo) throws Exception {
        return json.readTree(cuerpo);
    }
}
