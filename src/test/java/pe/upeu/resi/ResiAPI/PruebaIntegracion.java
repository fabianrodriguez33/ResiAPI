package pe.upeu.resi.ResiAPI;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.service.UsuarioService;

/** Base de las pruebas de integración: PostgreSQL real, MockMvc y utilidades para crear usuarios y tokens. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({RelojPrueba.Configuracion.class, CorreoPrueba.Configuracion.class})
public abstract class PruebaIntegracion {

    protected static final String CLAVE = "Clave-de-prueba-123";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected UsuarioService usuarioService;
    @Autowired
    protected RelojPrueba reloj;
    @Autowired
    protected CorreoPrueba correo;

    @AfterEach
    void liberarReloj() {
        reloj.liberar();
    }

    /** Fija el reloj en una hora local de Lima (UTC-5, sin horario de verano). */
    protected void fijarLima(java.time.LocalDate fecha, int hora, int minuto) {
        reloj.fijar(fecha.atTime(hora, minuto).atZone(java.time.ZoneId.of("America/Lima")).toInstant());
    }

    /** Lunes aleatorio lejano: evita choques con sesiones de culto de otras pruebas o ejecuciones. */
    protected java.time.LocalDate lunesAleatorio() {
        return java.time.LocalDate.of(2031, 1, 6).plusWeeks(new java.util.Random().nextInt(3000));
    }

    protected String qrDe(String username) throws Exception {
        String t = tokenDe(username);
        return leer(mvc.perform(MockMvcRequestBuilders.get("/api/v1/residentes/yo/qr").header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString()).get("contenido").asString();
    }

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

    protected <B extends AbstractMockHttpServletRequestBuilder<B>> B conToken(B req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    protected JsonNode leer(String cuerpo) throws Exception {
        return json.readTree(cuerpo);
    }

    /** Crea una habitación con la capacidad indicada y devuelve su id. */
    protected long crearHabitacion(String tokenAdmin, int capacidad) throws Exception {
        var res = mvc.perform(conToken(MockMvcRequestBuilders.post("/api/v1/habitaciones"), tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"T" + sufijo() + "\",\"capacidad\":" + capacidad + "}")).andReturn().getResponse();
        return leer(res.getContentAsString()).get("id").asLong();
    }

    /** Registra un residente (API de administración) y devuelve su id; el usuario queda en {@code ultimoUsuario}. */
    protected long crearResidente(String tokenAdmin) throws Exception {
        String user = "res_" + sufijo();
        var res = mvc.perform(conToken(MockMvcRequestBuilders.post("/api/v1/residentes"), tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Residente " + user
                        + "\",\"codigo\":\"" + codigoUniversitario() + "\",\"dni\":\"" + dniAleatorio() + "\"}")).andReturn().getResponse();
        if (res.getStatus() != 201) {
            throw new IllegalStateException("No se pudo crear el residente: " + res.getStatus());
        }
        ultimoUsuario = user;
        return leer(res.getContentAsString()).get("id").asLong();
    }

    /** Código de 9 dígitos al azar (como 202410354); la base de pruebas se reutiliza, por eso no es secuencial. */
    protected String codigoUniversitario() {
        return String.valueOf(100_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(900_000_000));
    }

    /** DNI de 8 dígitos al azar; es único en la base. */
    protected String dniAleatorio() {
        return String.valueOf(10_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(90_000_000));
    }

    protected String ultimoUsuario;
}
