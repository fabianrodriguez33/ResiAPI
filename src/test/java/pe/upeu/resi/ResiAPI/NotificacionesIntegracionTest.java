package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Las notificaciones llegan a la bandeja y, además, por push, correo verificado y SMS. */
class NotificacionesIntegracionTest extends PruebaIntegracion {

    private void esperar(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        for (int i = 0; i < 100 && !condicion.getAsBoolean(); i++) Thread.sleep(50);
        assertTrue(condicion.getAsBoolean(), "el envío asíncrono no llegó a tiempo");
    }

    @Test
    void elPermisoAvisaAlPadreAlResidenteYAlPastorPorTodosLosCanales() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario);
        String usuarioPadre = "padre_" + sufijo();
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre Prueba\",\"parentesco\":\"Padre\",\"principal\":true,\"username\":\"" + usuarioPadre
                        + "\",\"password\":\"" + CLAVE + "\"}")).andExpect(status().isCreated());
        String padre = tokenDe(usuarioPadre);
        String pastor = tokenDeRol(Rol.PRECEPTOR);

        // El padre deja teléfono y un correo verificado, y registra su celular para el push.
        String correoPadre = "padre_" + sufijo() + "@upeu.edu.pe";
        mvc.perform(conToken(patch("/api/v1/perfil"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + correoPadre + "\",\"telefono\":\"987654321\"}")).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/perfil/correo/codigo"), padre)).andExpect(status().isNoContent());
        mvc.perform(conToken(post("/api/v1/perfil/correo/verificar"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + correo.ultimoCodigo(correoPadre) + "\"}")).andExpect(status().isOk());
        String token = "fcm-" + sufijo();
        mvc.perform(conToken(post("/api/v1/notificaciones/dispositivos"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"plataforma\":\"ANDROID\"}")).andExpect(status().isNoContent());
        mvc.perform(conToken(post("/api/v1/notificaciones/dispositivos"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"x\",\"plataforma\":\"WINDOWS\"}")).andExpect(status().isBadRequest());

        // El residente pide un permiso: el padre recibe el aviso en la bandeja, por push, correo y SMS.
        fijarLima(lunesAleatorio(), 10, 0);
        Instant t0 = reloj.instant();
        long solicitud = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"LARGO\",\"motivo\":\"Trabajo\",\"destino\":\"La Era\",\"salida\":\"" + t0.plus(Duration.ofHours(2))
                        + "\",\"retorno\":\"" + t0.plus(Duration.ofHours(5)) + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        mvc.perform(conToken(get("/api/v1/notificaciones/no-leidas"), padre)).andExpect(jsonPath("$.cantidad").value(1));
        mvc.perform(conToken(get("/api/v1/notificaciones"), padre)).andExpect(jsonPath("$.contenido[0].titulo").value("Permiso por aprobar"))
                .andExpect(jsonPath("$.contenido[0].leida").value(false));
        esperar(() -> correo.push.stream().anyMatch(m -> m.destino().equals(token) && m.titulo().equals("Permiso por aprobar")));
        esperar(() -> correo.correos.stream().anyMatch(m -> m.destino().equals(correoPadre.toLowerCase())));
        esperar(() -> correo.sms.stream().anyMatch(m -> m.destino().equals("987654321") && m.texto().contains("Permiso por aprobar")));

        // El padre aprueba: avisa al residente y al pastor.
        mvc.perform(conToken(post("/api/v1/familia/solicitudes/" + solicitud + "/decision"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprobar\":true}")).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/notificaciones"), residente)).andExpect(jsonPath("$.contenido[0].titulo").value("Permiso aprobado"));
        mvc.perform(conToken(get("/api/v1/notificaciones"), pastor)).andExpect(jsonPath("$.contenido[0].titulo").value("Nuevo permiso aprobado"));

        // Leer: una por una y todas; nadie más puede tocar mis avisos.
        long aviso = leer(mvc.perform(conToken(get("/api/v1/notificaciones"), padre)).andReturn().getResponse().getContentAsString()).get("contenido").get(0).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/notificaciones/" + aviso + "/leida"), residente)).andExpect(status().isNotFound());
        mvc.perform(conToken(post("/api/v1/notificaciones/" + aviso + "/leida"), padre)).andExpect(status().isNoContent());
        mvc.perform(conToken(post("/api/v1/notificaciones/leidas"), padre)).andExpect(status().isNoContent());
        mvc.perform(conToken(get("/api/v1/notificaciones/no-leidas"), padre)).andExpect(jsonPath("$.cantidad").value(0));

        // El pastor anula el permiso: avisa a residente y padre.
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + solicitud + "/anular"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Reunión de residentes\"}")).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/notificaciones"), residente)).andExpect(jsonPath("$.contenido[0].titulo").value("Permiso anulado"));
        mvc.perform(conToken(get("/api/v1/notificaciones"), padre)).andExpect(jsonPath("$.contenido[0].titulo").value("Permiso anulado"));

        // Dejar de recibir push en ese teléfono.
        mvc.perform(conToken(delete("/api/v1/notificaciones/dispositivos").param("token", token), padre)).andExpect(status().isNoContent());
        int antes = correo.push.size();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + solicitud + "/anular"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"otra vez\"}")).andExpect(status().isConflict());
        assertEquals(antes, correo.push.size());
    }

    @Test
    void sinCuentaNoHayBandejaPeroSiLlegaElSmsAlTelefonoDelApoderado() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario);
        String telefono = "9" + (10_000_000 + new java.util.Random().nextInt(89_000_000));
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Tía Rosa\",\"telefono\":\"" + telefono + "\",\"parentesco\":\"Tía\",\"principal\":true}")).andExpect(status().isCreated());
        fijarLima(lunesAleatorio(), 10, 0);
        Instant t0 = reloj.instant();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residente).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CORTO\",\"motivo\":\"Trabajo\",\"destino\":\"La Era\",\"salida\":\"" + t0.plus(Duration.ofHours(2))
                        + "\",\"retorno\":\"" + t0.plus(Duration.ofHours(4)) + "\"}")).andExpect(status().isCreated());
        esperar(() -> correo.sms.stream().anyMatch(m -> m.destino().equals(telefono)));
    }
}
