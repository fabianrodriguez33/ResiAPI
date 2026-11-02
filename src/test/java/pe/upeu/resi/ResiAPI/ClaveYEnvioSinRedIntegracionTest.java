package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Contraseña provisional, cambio de contraseña, permisos pedidos sin red y revisión de ampliaciones. */
class ClaveYEnvioSinRedIntegracionTest extends PruebaIntegracion {

    private static final Pattern PROVISIONAL = Pattern.compile("provisional es (\\S{10})\\.");

    private String conCorreoVerificado(String usuario) throws Exception {
        String token = tokenDe(usuario);
        String email = usuario + "@upeu.edu.pe";
        mvc.perform(conToken(patch("/api/v1/perfil"), token).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/perfil/correo/codigo"), token)).andExpect(status().isNoContent());
        mvc.perform(conToken(post("/api/v1/perfil/correo/verificar"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + correo.ultimoCodigo(email) + "\"}")).andExpect(status().isOk());
        return email;
    }

    private int login(String usuario, String clave) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + usuario + "\",\"password\":\"" + clave + "\"}")).andReturn().getResponse().getStatus();
    }

    private void olvide(String usuario) throws Exception {
        mvc.perform(post("/api/v1/auth/olvide-clave").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + usuario + "\"}")).andExpect(status().isNoContent());
    }

    @Test
    void laClaveProvisionalLlegaPorCorreoVenceYSeCambiaAlEntrar() throws Exception {
        String usuario = crearPersonal(Rol.GARITA);
        String email = conCorreoVerificado(usuario);
        Instant inicio = Instant.parse("2031-05-05T15:00:00Z");
        reloj.fijar(inicio);

        olvide(usuario);
        var envio = correo.correos.stream().filter(m -> m.destino().equals(email)).findFirst().orElseThrow();
        var m = PROVISIONAL.matcher(envio.texto());
        assertTrue(m.find(), "el correo trae la clave provisional");
        String provisional = m.group(1);
        // Un segundo pedido inmediato no envía otro correo.
        olvide(usuario);
        assertEquals(1, correo.correos.stream().filter(x -> x.destino().equals(email)).count());

        // La contraseña de siempre sigue valiendo y no pide cambiarla; la provisional entra y sí lo pide.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + usuario + "\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarClave").value(false));
        var res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + usuario + "\",\"password\":\"" + provisional + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarClave").value(true)).andReturn().getResponse().getContentAsString();
        String token = leer(res).get("token").asString();

        // Cambio: la actual debe ser correcta, la nueva distinta y de 8 o más caracteres.
        String cambio = "/api/v1/auth/cambiar-clave";
        mvc.perform(conToken(post(cambio), token).contentType(MediaType.APPLICATION_JSON).content("{\"claveActual\":\"mala-mala-1\",\"claveNueva\":\"Nueva-clave-2031\"}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(post(cambio), token).contentType(MediaType.APPLICATION_JSON).content("{\"claveActual\":\"" + provisional + "\",\"claveNueva\":\"corta\"}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(post(cambio), token).contentType(MediaType.APPLICATION_JSON).content("{\"claveActual\":\"" + provisional + "\",\"claveNueva\":\"" + provisional + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(post(cambio), token).contentType(MediaType.APPLICATION_JSON).content("{\"claveActual\":\"" + provisional + "\",\"claveNueva\":\"Nueva-clave-2031\"}")).andExpect(status().isNoContent());

        assertEquals(200, login(usuario, "Nueva-clave-2031"));
        assertEquals(401, login(usuario, CLAVE));
        assertEquals(401, login(usuario, provisional)); // ya no sirve
    }

    @Test
    void laProvisionalVenceALos30MinutosYNoRevelaQuienExiste() throws Exception {
        String usuario = crearPersonal(Rol.PRECEPTOR);
        String email = conCorreoVerificado(usuario);
        Instant inicio = Instant.parse("2031-05-06T15:00:00Z");
        reloj.fijar(inicio);
        olvide(usuario);
        var m = PROVISIONAL.matcher(correo.correos.stream().filter(x -> x.destino().equals(email)).findFirst().orElseThrow().texto());
        assertTrue(m.find());
        reloj.fijar(inicio.plus(Duration.ofMinutes(31)));
        assertEquals(401, login(usuario, m.group(1)));

        // Sin correo verificado o inexistente: misma respuesta y ningún correo.
        int antes = correo.correos.size();
        olvide(crearPersonal(Rol.GARITA));
        olvide("nadie_" + sufijo());
        assertEquals(antes, correo.correos.size());
    }

    @Test
    void unPermisoPedidoSinRedSeEnviaUnaSolaVezAunqueSeRepita() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario);
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre\",\"parentesco\":\"Padre\",\"principal\":true,\"username\":\"p_" + sufijo() + "\",\"password\":\"" + CLAVE + "\"}")).andExpect(status().isCreated());
        fijarLima(lunesAleatorio(), 10, 0);
        Instant t0 = reloj.instant();
        String clave = "app-" + sufijo();
        String cuerpo = "{\"tipo\":\"LARGO\",\"motivo\":\"Trabajo\",\"destino\":\"La Era\",\"salida\":\"" + t0.plus(Duration.ofHours(3)) + "\",\"retorno\":\"" + t0.plus(Duration.ofHours(6))
                + "\",\"claveCliente\":\"" + clave + "\"}";
        long primera = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residente).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        long repetida = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residente).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertEquals(primera, repetida);
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/mias"), residente)).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void elPastorRevisaAmpliacionesYJustificacionesConFotoYElResidenteSeEntera() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), pastor = tokenDeRol(Rol.PRECEPTOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario);
        mvc.perform(conToken(patch("/api/v1/residentes/" + id), admin).contentType(MediaType.APPLICATION_JSON).content("{\"puedeSolicitarSolo\":true}")).andExpect(status().isOk());
        fijarLima(lunesAleatorio(), 10, 0);
        Instant t0 = reloj.instant();
        long permiso = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"LARGO\",\"motivo\":\"Trabajo\",\"destino\":\"La Era\",\"salida\":\"" + t0.plus(Duration.ofMinutes(10)) + "\",\"retorno\":\"" + t0.plus(Duration.ofHours(4)) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        // La ampliación exige foto; se sube una imagen (PNG mínimo) y se pide volver más tarde.
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        long foto = leer(mvc.perform(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "foto.png", "image/png", png))
                .header("Authorization", "Bearer " + residente)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + permiso + "/ampliaciones"), residente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Me retrasé\",\"retornoNuevo\":\"" + t0.plus(Duration.ofHours(6)) + "\"}")).andExpect(status().isBadRequest());
        long ampliacion = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + permiso + "/ampliaciones"), residente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Me retrasé\",\"retornoNuevo\":\"" + t0.plus(Duration.ofHours(6)) + "\",\"evidenciaId\":" + foto + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        // El pastor la ve con el nombre del residente, descarga la foto y la aprueba.
        mvc.perform(conToken(get("/api/v1/permisos/ampliaciones/pendientes"), pastor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[?(@.id == " + ampliacion + ")].residenteNombre").isNotEmpty())
                .andExpect(jsonPath("$.contenido[?(@.id == " + ampliacion + ")].evidenciaId").value(org.hamcrest.Matchers.hasItem((int) foto)));
        mvc.perform(conToken(get("/api/v1/evidencias/" + foto), pastor)).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/permisos/ampliaciones/" + ampliacion + "/decision"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"));
        mvc.perform(conToken(get("/api/v1/notificaciones"), residente)).andExpect(jsonPath("$.contenido[0].titulo").value("Ampliación aprobada"));
    }
}
