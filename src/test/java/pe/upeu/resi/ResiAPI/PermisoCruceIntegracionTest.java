package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Un residente no puede tener dos permisos vigentes cuyos horarios se crucen. */
class PermisoCruceIntegracionTest extends PruebaIntegracion {

    private static final String SOLICITUDES = "/api/v1/permisos/solicitudes";
    private static final String MENSAJE = "Ya hay un permiso pendiente o aprobado que se superpone con ese horario";

    private record Escenario(String preceptor, String residente) {
    }

    private LocalDate dia;

    /** Residente que envía directo al pastor; el reloj queda el lunes a las 10:00. */
    private Escenario escenario() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String usuario = ultimoUsuario;
        mvc.perform(conToken(patch("/api/v1/residentes/" + id), admin).contentType(MediaType.APPLICATION_JSON).content("{\"puedeSolicitarSolo\":true}"))
                .andExpect(status().isOk());
        dia = lunesAleatorio();
        fijarLima(dia, 10, 0);
        return new Escenario(tokenDeRol(Rol.PRECEPTOR), tokenDe(usuario));
    }

    private Instant a(String hora) {
        return dia.atTime(java.time.LocalTime.parse(hora)).atZone(ZoneId.of("America/Lima")).toInstant();
    }

    private MockHttpServletResponse enviar(Escenario e, String desde, String hasta, String clave) throws Exception {
        String extra = clave == null ? "" : ",\"claveCliente\":\"" + clave + "\"";
        return mvc.perform(conToken(post(SOLICITUDES), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CORTO\",\"motivo\":\"Compras\",\"destino\":\"Centro\",\"salida\":\"" + a(desde) + "\",\"retorno\":\"" + a(hasta) + "\"" + extra + "}"))
                .andReturn().getResponse();
    }

    private long crear(Escenario e, String desde, String hasta) throws Exception {
        var res = enviar(e, desde, hasta, null);
        assertEquals(201, res.getStatus(), desde + "-" + hasta);
        return leer(res.getContentAsString()).get("id").asLong();
    }

    private void rechazadoPorCruce(Escenario e, String desde, String hasta) throws Exception {
        var res = enviar(e, desde, hasta, null);
        assertEquals(409, res.getStatus(), desde + "-" + hasta);
        assertEquals(MENSAJE, leer(res.getContentAsString()).get("message").asString());
    }

    @Test
    void cualquierCruceSeRechazaYLosContiguosSePermiten() throws Exception {
        var e = escenario();
        long primero = crear(e, "17:00", "18:00");
        rechazadoPorCruce(e, "17:00", "18:00");
        rechazadoPorCruce(e, "17:40", "18:30");
        rechazadoPorCruce(e, "16:30", "17:10");
        rechazadoPorCruce(e, "17:15", "17:45");
        rechazadoPorCruce(e, "16:30", "18:30");
        crear(e, "18:00", "19:00");
        crear(e, "16:00", "17:00");
        // Aprobado sigue bloqueando
        aprobarComoPastor(e.preceptor(), primero);
        rechazadoPorCruce(e, "17:30", "18:00");
    }

    @Test
    void unPermisoCanceladoORechazadoNoBloquea() throws Exception {
        var e = escenario();
        long cancelado = crear(e, "17:00", "18:00");
        mvc.perform(conToken(post(SOLICITUDES + "/" + cancelado + "/cancelar"), e.residente())).andExpect(status().isOk());
        long rechazado = crear(e, "17:00", "18:00");
        mvc.perform(conToken(post(SOLICITUDES + "/" + rechazado + "/decision"), e.preceptor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprobar\":false,\"comentario\":\"No\"}")).andExpect(status().isOk());
        crear(e, "17:00", "18:00");
    }

    private MockHttpServletResponse ampliar(Escenario e, long id, String hasta) throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var foto = mvc.perform(conToken(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "foto.png", "image/png", png)), e.residente()))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long evidencia = leer(foto.getContentAsString()).get("id").asLong();
        return mvc.perform(conToken(post(SOLICITUDES + "/" + id + "/ampliaciones"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Tráfico\",\"retornoNuevo\":\"" + a(hasta) + "\",\"evidenciaId\":" + evidencia + "}")).andReturn().getResponse();
    }

    @Test
    void noSePideUnaAmpliacionQuePiseOtroPermiso() throws Exception {
        var e = escenario();
        long id = crear(e, "17:00", "18:00");
        aprobarComoPastor(e.preceptor(), id);
        crear(e, "19:00", "20:00");
        assertEquals(409, ampliar(e, id, "19:30").getStatus());
        // Hasta justo cuando empieza el siguiente sí; el permiso ampliado no cuenta contra sí mismo
        assertEquals(201, ampliar(e, id, "19:00").getStatus());
    }

    @Test
    void noSeApruebaUnaAmpliacionSiDespuesSePidioOtroPermisoEnEseRango() throws Exception {
        var e = escenario();
        long id = crear(e, "17:00", "18:00");
        aprobarComoPastor(e.preceptor(), id);
        var res = ampliar(e, id, "19:30");
        assertEquals(201, res.getStatus());
        String decision = "/api/v1/permisos/ampliaciones/" + leer(res.getContentAsString()).get("id").asLong() + "/decision";
        crear(e, "19:00", "20:00");
        mvc.perform(conToken(post(decision), e.preceptor()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(MENSAJE));
        mvc.perform(conToken(get(SOLICITUDES + "/" + id), e.residente())).andExpect(jsonPath("$.retornoAutorizado").value(a("18:00").toString()))
                .andExpect(jsonPath("$.ampliaciones[0].estado").value("PENDIENTE"));
        // Rechazarla sigue siendo posible
        mvc.perform(conToken(post(decision), e.preceptor()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":false}"))
                .andExpect(status().isOk());
    }

    /** Dos envíos lanzados a la vez; devuelve las dos respuestas. */
    private List<MockHttpServletResponse> aLaVez(Escenario e, String clave) throws Exception {
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var salida = new CountDownLatch(1);
            var envios = java.util.stream.IntStream.range(0, 2).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                try {
                    salida.await();
                    return enviar(e, "17:00", "18:00", clave);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }, hilos)).toList();
            salida.countDown();
            return envios.stream().map(CompletableFuture::join).toList();
        }
    }

    @Test
    void dosEnviosSimultaneosCreanUnSoloPermiso() throws Exception {
        for (int intento = 0; intento < 5; intento++) {
            var estados = aLaVez(escenario(), null).stream().map(MockHttpServletResponse::getStatus).sorted().toList();
            assertEquals(List.of(201, 409), estados, "intento " + intento);
        }
    }

    @Test
    void elMismoEnvioRepetidoALaVezDevuelveElMismoPermiso() throws Exception {
        for (int intento = 0; intento < 5; intento++) {
            var respuestas = aLaVez(escenario(), "doble-toque-" + sufijo());
            assertEquals(201, respuestas.get(0).getStatus(), "intento " + intento);
            assertEquals(201, respuestas.get(1).getStatus(), "intento " + intento);
            assertEquals(leer(respuestas.get(0).getContentAsString()).get("id"), leer(respuestas.get(1).getContentAsString()).get("id"));
        }
    }
}
