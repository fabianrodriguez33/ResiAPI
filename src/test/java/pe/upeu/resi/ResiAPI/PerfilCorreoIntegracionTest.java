package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Perfil de cualquier rol (pastor, garita, padre…) y verificación del correo con un código de 6 dígitos. */
class PerfilCorreoIntegracionTest extends PruebaIntegracion {

    private void guardar(String token, String cuerpo) throws Exception {
        mvc.perform(conToken(patch("/api/v1/perfil"), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk());
    }

    private int pedirCodigo(String token) throws Exception {
        return mvc.perform(conToken(post("/api/v1/perfil/correo/codigo"), token)).andReturn().getResponse().getStatus();
    }

    private int verificar(String token, String codigo) throws Exception {
        return mvc.perform(conToken(post("/api/v1/perfil/correo/verificar"), token).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void elPastorEditaSuPerfilYVerificaSuCorreo() throws Exception {
        String user = crearPersonal(Rol.PRECEPTOR);
        String token = tokenDe(user);
        String correo = "pastor." + sufijo() + "@upeu.edu.pe";

        mvc.perform(conToken(get("/api/v1/perfil"), token)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("PRECEPTOR"))
                .andExpect(jsonPath("$.emailVerificado").value(false)).andExpect(jsonPath("$.dni").doesNotExist());
        assertEquals(409, pedirCodigo(token), "sin correo guardado no hay a dónde enviarlo");

        guardar(token, "{\"email\":\"" + correo + "\",\"telefono\":\"987654321\",\"fechaNacimiento\":\"1985-09-12\"}");
        mvc.perform(conToken(get("/api/v1/perfil"), token)).andExpect(jsonPath("$.email").value(correo)).andExpect(jsonPath("$.telefono").value("987654321"))
                .andExpect(jsonPath("$.fechaNacimiento").value("1985-09-12")).andExpect(jsonPath("$.emailVerificado").value(false));

        assertEquals(204, pedirCodigo(token));
        String codigo = correo(correo);
        assertNotNull(codigo);
        assertEquals(6, codigo.length());
        String malo = codigo.equals("000000") ? "111111" : "000000";
        assertEquals(409, verificar(token, malo));
        assertEquals(400, verificar(token, "12"), "el código tiene 6 dígitos");
        assertEquals(200, verificar(token, codigo));
        mvc.perform(conToken(get("/api/v1/perfil"), token)).andExpect(jsonPath("$.emailVerificado").value(true));
        assertEquals(409, pedirCodigo(token), "ya está verificado");

        // Cambiar el correo lo deja sin verificar; guardar el mismo no.
        guardar(token, "{\"email\":\"" + correo.toUpperCase() + "\"}");
        mvc.perform(conToken(get("/api/v1/perfil"), token)).andExpect(jsonPath("$.emailVerificado").value(true));
        String otro = "otro." + sufijo() + "@upeu.edu.pe";
        guardar(token, "{\"email\":\"" + otro + "\"}");
        mvc.perform(conToken(get("/api/v1/perfil"), token)).andExpect(jsonPath("$.emailVerificado").value(false));
    }

    private String correo(String destino) {
        return correo.ultimoCodigo(destino);
    }

    @Test
    void elCodigoTieneIntentosLimitadosVenceYSePideConEspera() throws Exception {
        String token = tokenDe(crearPersonal(Rol.GARITA));
        String correo = "garita." + sufijo() + "@upeu.edu.pe";
        guardar(token, "{\"email\":\"" + correo + "\"}");

        fijarLima(lunesAleatorio(), 9, 0);
        assertEquals(204, pedirCodigo(token));
        assertEquals(409, pedirCodigo(token), "hay que esperar un minuto");
        String primero = correo(correo);
        reloj.fijar(reloj.instant().plusSeconds(61));
        assertEquals(204, pedirCodigo(token));

        // Cinco intentos fallidos agotan el código aunque después se escriba el correcto.
        String codigo = correo(correo);
        String malo = codigo.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertEquals(409, verificar(token, malo));
        }
        assertEquals(409, verificar(token, codigo));
        assertEquals(409, verificar(token, codigo), "el código se descartó");

        // Un código vencido no sirve.
        reloj.fijar(reloj.instant().plusSeconds(120));
        assertEquals(204, pedirCodigo(token));
        String vigente = correo(correo);
        reloj.fijar(reloj.instant().plus(Duration.ofMinutes(16)));
        assertEquals(409, verificar(token, vigente));
        assertNotNull(primero);
    }

    @Test
    void unCorreoVerificadoNoSePuedeVerificarEnOtraCuenta() throws Exception {
        String a = tokenDe(crearPersonal(Rol.PRECEPTOR)), b = tokenDe(crearPersonal(Rol.GARITA));
        String correo = "compartido." + sufijo() + "@upeu.edu.pe";
        fijarLima(lunesAleatorio(), 9, 0);
        guardar(a, "{\"email\":\"" + correo + "\"}");
        assertEquals(204, pedirCodigo(a));
        assertEquals(200, verificar(a, correo(correo)));

        guardar(b, "{\"email\":\"" + correo + "\"}");
        assertEquals(204, pedirCodigo(b));
        assertEquals(409, verificar(b, correo(correo)));
    }

    @Test
    void soloLaAdministracionCreaCuentasDeCualquierRolMenosResidentes() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x_" + sufijo() + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Persona Prueba\",\"rol\":\"RESIDENTE\"}"))
                .andExpect(status().isBadRequest());
        for (String rol : new String[] {"ADMINISTRADOR", "APODERADO"}) {
            mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"x_" + sufijo() + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Persona Prueba\",\"rol\":\"" + rol + "\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value(rol));
        }
        // Un padre creado así se vincula luego a su hijo con su mismo usuario.
        long hijo = crearResidente(admin);
        String padre = "padre_" + sufijo();
        mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + padre + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Padre Prueba\",\"rol\":\"APODERADO\"}")).andExpect(status().isCreated());
        mvc.perform(conToken(post("/api/v1/residentes/" + hijo + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre Prueba\",\"parentesco\":\"Padre\",\"principal\":true,\"username\":\"" + padre + "\"}")).andExpect(status().isCreated());
        mvc.perform(conToken(get("/api/v1/familia/hijos"), tokenDe(padre))).andExpect(status().isOk()).andExpect(jsonPath("$[0].residenteId").value(hijo));
        String dni = dniAleatorio();
        mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"p_" + sufijo() + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Pastor Prueba Uno\",\"rol\":\"PRECEPTOR\",\"dni\":\"" + dni
                        + "\",\"fechaNacimiento\":\"1980-01-30\"}"))
                .andExpect(status().isCreated());
        mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"g_" + sufijo() + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Garita Prueba Uno\",\"rol\":\"GARITA\",\"dni\":\"" + dni + "\"}"))
                .andExpect(status().isConflict());
        // Ni el pastor ni el residente crean cuentas.
        mvc.perform(conToken(post("/api/v1/usuarios"), tokenDeRol(Rol.PRECEPTOR)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"z_" + sufijo() + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Persona Prueba\",\"rol\":\"GARITA\"}"))
                .andExpect(status().isForbidden());
    }
}
