package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.service.PermisoService;

class PermisoGaritaIntegracionTest extends PruebaIntegracion {

    @Autowired
    PermisoService permisoService;

    /** Contexto de un residente con apoderado y sus tokens. */
    private record Escenario(String admin, String preceptor, String garita, String residente, String usuario, long residenteId, String qr) {
    }

    private Escenario escenario() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Apoderado Prueba\",\"telefono\":\"999111222\",\"parentesco\":\"Padre\",\"principal\":true}")).andExpect(status().isCreated());
        String user = ultimoUsuario;
        return new Escenario(admin, tokenDeRol(Rol.PRECEPTOR), tokenDeRol(Rol.GARITA), tokenDe(user), user, id, qrDe(user));
    }

    private String cuerpoSolicitud(String tipo, Instant salida, Instant retorno) {
        return "{\"tipo\":\"" + tipo + "\",\"motivo\":\"Visita familiar\",\"destino\":\"Casa\",\"salida\":\"" + salida + "\",\"retorno\":\"" + retorno + "\"}";
    }

    private long crearSolicitud(Escenario e, Instant salida, Instant retorno) throws Exception {
        var res = mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud(Duration.between(salida, retorno).compareTo(Duration.ofHours(2)) > 0 ? "LARGO" : "CORTO", salida, retorno))).andExpect(status().isCreated()).andReturn().getResponse();
        return leer(res.getContentAsString()).get("id").asLong();
    }

    /** Lleva una solicitud hasta APROBADO por el flujo completo (apoderado + preceptor). */
    private long solicitudAprobada(Escenario e, Instant salida, Instant retorno) throws Exception {
        long id = crearSolicitud(e, salida, retorno);
        String token = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/enlace-apoderado"), e.preceptor()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asString();
        mvc.perform(post("/api/v1/publico/apoderado/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}")).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.residente())).andExpect(jsonPath("$.estado").value("APROBADO"));
        return id;
    }

    private String verificar(Escenario e, String qr) throws Exception {
        var res = mvc.perform(conToken(post("/api/v1/garita/verificaciones"), e.garita()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qr + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return leer(res).get("resultado").asString();
    }

    private int movimiento(Escenario e, String qr, String tipo) throws Exception {
        return mvc.perform(conToken(post("/api/v1/garita/movimientos"), e.garita()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qr + "\",\"tipo\":\"" + tipo + "\"}")).andReturn().getResponse().getStatus();
    }

    private Instant base() {
        fijarLima(lunesAleatorio(), 10, 0);
        return reloj.instant();
    }

    private long subirEvidencia(String token) throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var res = mvc.perform(conToken(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "foto.png", "image/png", png)), token))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return leer(res.getContentAsString()).get("id").asLong();
    }

    // ------------------------------------------------------------------ flujo completo

    @Test
    void flujoCompletoSolicitudApoderadoYGarita() throws Exception {
        var e = escenario();
        Instant t0 = base();
        Instant salida = t0.plus(Duration.ofHours(2)), retorno = t0.plus(Duration.ofHours(4));
        long id = crearSolicitud(e, salida, retorno);

        // Mientras el apoderado no responde, el permiso no autoriza nada; el pastor ya no tiene cómo aprobarlo.
        assertEquals("SIN_PERMISO", verificar(e, e.qr()), "un permiso pendiente no autoriza la salida");
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/decision"), e.preceptor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprobar\":true}")).andExpect(status().is4xxClientError());
        // El residente no puede generar el enlace
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/enlace-apoderado"), e.residente())).andExpect(status().isForbidden());

        String token = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/enlace-apoderado"), e.preceptor()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asString();
        // La página del apoderado muestra datos mínimos y el token es de un solo uso
        mvc.perform(get("/publico/aprobacion/" + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/publico/apoderado/" + token)).andExpect(status().isOk()).andExpect(jsonPath("$.destino").value("Casa"));
        mvc.perform(post("/api/v1/publico/apoderado/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/publico/apoderado/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":false}")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/publico/apoderado/" + token)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/publico/apoderado/token-inventado")).andExpect(status().isNotFound());

        // Con el visto bueno del apoderado queda aprobado y el pastor lo ve en su lista.
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.residente())).andExpect(jsonPath("$.estado").value("APROBADO"));
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes").param("estado", "APROBADO"), e.preceptor())).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.preceptor())).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADO"));

        // Antes de la hora de salida
        assertEquals("PERMISO_AUN_NO_VIGENTE", verificar(e, e.qr()));
        assertEquals(409, movimiento(e, e.qr(), "SALIDA"));

        // En horario: salida
        reloj.fijar(salida.plusSeconds(60));
        assertEquals("AUTORIZADO_SALIDA", verificar(e, e.qr()));
        assertEquals(201, movimiento(e, e.qr(), "SALIDA"));
        assertEquals(409, movimiento(e, e.qr(), "SALIDA"), "no se permiten dos salidas consecutivas");
        assertEquals("AUTORIZADO_RETORNO", verificar(e, e.qr()));

        // Retorno tardío (30 min): se registra pero se señala el retraso
        reloj.fijar(retorno.plus(Duration.ofMinutes(30)));
        assertEquals("RETORNO_FUERA_DE_HORARIO", verificar(e, e.qr()));
        mvc.perform(conToken(post("/api/v1/garita/movimientos"), e.garita()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + e.qr() + "\",\"tipo\":\"RETORNO\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.minutosTarde").value(30));
        assertEquals(409, movimiento(e, e.qr(), "RETORNO"), "no se permiten dos retornos consecutivos");

        // El permiso quedó consumido
        assertEquals("SIN_PERMISO", verificar(e, e.qr()));
        mvc.perform(conToken(get("/api/v1/residentes/yo/movimientos"), e.residente())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
        String auditoria = mvc.perform(conToken(get("/api/v1/auditoria?tamanio=200"), e.admin())).andReturn().getResponse().getContentAsString();
        assertTrue(auditoria.contains("MOVIMIENTO_SALIDA"));
    }

    @Test
    void garitaNoPuedeAprobarNiElResidenteRegistrarMovimientos() throws Exception {
        var e = escenario();
        base();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/1/decision"), e.garita()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().is4xxClientError());
        assertEquals(403, mvc.perform(conToken(post("/api/v1/garita/movimientos"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + e.qr() + "\",\"tipo\":\"SALIDA\"}")).andReturn().getResponse().getStatus());
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes"), e.garita())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ garita: casos negativos

    @Test
    void salidaSinPermisoQrRevocadoYResidenteInactivo() throws Exception {
        var e = escenario();
        base();
        assertEquals("SIN_PERMISO", verificar(e, e.qr()));
        assertEquals(409, movimiento(e, e.qr(), "SALIDA"));
        assertEquals(409, movimiento(e, e.qr(), "RETORNO"), "retorno sin salida previa");
        assertEquals("QR_INVALIDO", verificar(e, "RESIUP1.falso.falso"));

        mvc.perform(conToken(post("/api/v1/residentes/" + e.residenteId() + "/qr/regenerar"), e.admin())).andExpect(status().isOk());
        assertEquals("QR_REVOCADO", verificar(e, e.qr()));
        assertEquals(409, movimiento(e, e.qr(), "SALIDA"));

        String nuevo = qrDe(e.usuario());
        assertEquals("SIN_PERMISO", verificar(e, nuevo));
        mvc.perform(conToken(patch("/api/v1/residentes/" + e.residenteId()), e.admin()).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        assertEquals("RESIDENTE_INACTIVO", verificar(e, nuevo));
    }

    @Test
    void permisoVencidoNoAutorizaLaSalida() throws Exception {
        var e = escenario();
        Instant t0 = base();
        solicitudAprobada(e, t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(2)));
        reloj.fijar(t0.plus(Duration.ofHours(3)));
        assertEquals("PERMISO_VENCIDO", verificar(e, e.qr()));
        assertEquals(409, movimiento(e, e.qr(), "SALIDA"));
    }

    @Test
    void soloSePuedeAnularElUltimoMovimientoYQuedaAuditado() throws Exception {
        var e = escenario();
        Instant t0 = base();
        solicitudAprobada(e, t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(3)));
        reloj.fijar(t0.plus(Duration.ofHours(1)).plusSeconds(5));
        var salida = leer(mvc.perform(conToken(post("/api/v1/garita/movimientos"), e.garita()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + e.qr() + "\",\"tipo\":\"SALIDA\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long idSalida = salida.get("id").asLong();
        assertEquals(403, mvc.perform(conToken(post("/api/v1/garita/movimientos/" + idSalida + "/anular"), e.garita()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"x\"}")).andReturn().getResponse().getStatus(), "garita no corrige registros");
        mvc.perform(conToken(post("/api/v1/garita/movimientos/" + idSalida + "/anular"), e.preceptor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Escaneo accidental\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.anulado").value(true));
        // Tras anular, el residente vuelve a estar dentro y puede salir de nuevo con el mismo permiso
        assertEquals("AUTORIZADO_SALIDA", verificar(e, e.qr()));
    }

    // ------------------------------------------------------------------ solicitud: reglas

    @Test
    void apoderadoRechazaYElEnlaceExpira() throws Exception {
        var e = escenario();
        Instant t0 = base();
        long id = crearSolicitud(e, t0.plus(Duration.ofDays(4)), t0.plus(Duration.ofDays(4)).plus(Duration.ofHours(2)));
        String token = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/enlace-apoderado"), e.preceptor()))
                .andReturn().getResponse().getContentAsString()).get("token").asString();
        reloj.fijar(t0.plus(Duration.ofHours(49)));
        mvc.perform(get("/api/v1/publico/apoderado/" + token)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/publico/apoderado/" + token).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}")).andExpect(status().isConflict());

        String token2 = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/enlace-apoderado"), e.preceptor()))
                .andReturn().getResponse().getContentAsString()).get("token").asString();
        assertNotEquals(token, token2);
        mvc.perform(post("/api/v1/publico/apoderado/" + token2).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":false}")).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.residente())).andExpect(jsonPath("$.estado").value("RECHAZADO"));
    }

    @Test
    void validacionesDeLaSolicitudYSuperposicion() throws Exception {
        var e = escenario();
        Instant t0 = base();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.motivo").exists());
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("CORTO", t0.minus(Duration.ofHours(3)), t0.plus(Duration.ofHours(1))))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.salida").exists());
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("CORTO", t0.plus(Duration.ofHours(5)), t0.plus(Duration.ofHours(4))))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.retorno").exists());

        long id = crearSolicitud(e, t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(4)));
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("LARGO", t0.plus(Duration.ofHours(3)), t0.plus(Duration.ofHours(6))))).andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/cancelar"), e.residente())).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/cancelar"), e.residente())).andExpect(status().isConflict());
        // Con la anterior cancelada, ya no hay superposición
        crearSolicitud(e, t0.plus(Duration.ofHours(3)), t0.plus(Duration.ofHours(5)));
    }

    @Test
    void elTipoDeSalidaDependeDeLaDuracionYDelRegresoElMismoDia() throws Exception {
        var e = escenario();
        Instant t0 = base();
        // Corto: 2 horas exactas sí; 3 horas no (debe pedirse largo).
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("CORTO", t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(3))))).andExpect(status().isCreated());
        var otro = escenario();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), otro.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("CORTO", t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(4)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.retorno").exists());
        // El mismo horario de 3 horas como permiso largo es válido.
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), otro.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("LARGO", t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(4))))).andExpect(status().isCreated());
        // Larga de 2 horas o menos: debe ser corta.
        var tercero = escenario();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), tercero.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("LARGO", t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(3)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.retorno").exists());
        // Larga sin tope de horas mientras regrese el mismo día (10:00 a 23:59 = casi 14 h).
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), tercero.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("LARGO", t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(13)).plus(Duration.ofMinutes(59)))))
                .andExpect(status().isCreated());
        // Larga que cruza la medianoche: debe pedirse pernoctar fuera.
        var cuarto = escenario();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), cuarto.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("LARGO", t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(14)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.retorno").exists());
        mvc.perform(conToken(put("/api/v1/configuracion/permiso.corto.max_horas"), e.admin()).contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fueraDelHorarioDeEnvioSeRechazaYLoPedidoSinConexionSeEvaluaConLaHoraDelTelefono() throws Exception {
        var e = escenario();
        var lunes = lunesAleatorio();
        var viernes = lunes.plusDays(4);
        // Viernes 15:00 (cerrado desde las 14:00): se rechaza con el horario.
        fijarLima(viernes, 15, 0);
        Instant ahora = reloj.instant();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("CORTO", ahora.plus(Duration.ofHours(3)), ahora.plus(Duration.ofHours(4)))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2:00 p. m.")));
        // Pedido a las 13:00 sin conexión y enviado a las 15:00 (con red): vale la hora en que se creó.
        Instant creada = viernes.atTime(13, 0).atZone(java.time.ZoneId.of("America/Lima")).toInstant();
        String cuerpo = cuerpoSolicitud("CORTO", ahora.plus(Duration.ofHours(3)), ahora.plus(Duration.ofHours(4)));
        cuerpo = cuerpo.substring(0, cuerpo.length() - 1) + ",\"claveCliente\":\"app-prueba-horario\",\"creadaEn\":\"" + creada + "\"}";
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), e.residente()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
        // Sin clave de la app, la hora declarada no se usa.
        var otro = escenario();
        String sinClave = cuerpoSolicitud("CORTO", ahora.plus(Duration.ofHours(3)), ahora.plus(Duration.ofHours(4)));
        sinClave = sinClave.substring(0, sinClave.length() - 1) + ",\"creadaEn\":\"" + creada + "\"}";
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), otro.residente()).contentType(MediaType.APPLICATION_JSON).content(sinClave))
                .andExpect(status().isConflict());
        // Sábado 18:30 abre de nuevo.
        fijarLima(viernes.plusDays(1), 18, 30);
        Instant sabado = reloj.instant();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), otro.residente()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoSolicitud("CORTO", sabado.plus(Duration.ofHours(12)), sabado.plus(Duration.ofHours(13)))))
                .andExpect(status().isCreated());
    }

    @Test
    void lasSolicitudesPendientesVencenCuandoPasaLaHoraDeSalida() throws Exception {
        var e = escenario();
        Instant t0 = base();
        long id = crearSolicitud(e, t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(2)));
        reloj.fijar(t0.plus(Duration.ofHours(1)).plusSeconds(1));
        assertTrue(permisoService.vencerPendientes() >= 1);
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.residente())).andExpect(jsonPath("$.estado").value("VENCIDO"));
    }

    // ------------------------------------------------------------------ ampliación y justificación

    @Test
    void ampliacionAntesDelVencimientoActualizaElHorarioAutorizadoConservandoElOriginal() throws Exception {
        var e = escenario();
        Instant t0 = base();
        Instant retorno = t0.plus(Duration.ofHours(4));
        long id = solicitudAprobada(e, t0.plus(Duration.ofHours(1)), retorno);
        reloj.fijar(t0.plus(Duration.ofHours(2)));
        String nuevo = retorno.plus(Duration.ofHours(2)).toString();

        // La evidencia es obligatoria (propuesta, configurable)
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/ampliaciones"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Retraso del transporte\",\"retornoNuevo\":\"" + nuevo + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.evidenciaId").exists());
        long evidencia = subirEvidencia(e.residente());
        var amp = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/ampliaciones"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Retraso del transporte\",\"retornoNuevo\":\"" + nuevo + "\",\"evidenciaId\":" + evidencia + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long ampId = amp.get("id").asLong();
        // No se acepta una segunda ampliación mientras hay una pendiente
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/ampliaciones"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Otra\",\"retornoNuevo\":\"" + nuevo + "\",\"evidenciaId\":" + evidencia + "}")).andExpect(status().isConflict());
        // El residente no aprueba su propia ampliación
        mvc.perform(conToken(post("/api/v1/permisos/ampliaciones/" + ampId + "/decision"), e.residente()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/permisos/ampliaciones/" + ampId + "/decision"), e.preceptor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprobar\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"));

        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.residente()))
                .andExpect(jsonPath("$.retornoSolicitado").value(retorno.toString()))
                .andExpect(jsonPath("$.retornoAutorizado").value(nuevo))
                .andExpect(jsonPath("$.ampliaciones[0].retornoOriginal").value(retorno.toString()));

        // Otro residente no puede usar mi evidencia ni descargarla
        var otro = escenario();
        mvc.perform(conToken(get("/api/v1/evidencias/" + evidencia), otro.residente())).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/evidencias/" + evidencia), e.preceptor())).andExpect(status().isOk());
    }

    @Test
    void despuesDelVencimientoSoloAplicaJustificacionQueNoModificaElPermiso() throws Exception {
        var e = escenario();
        Instant t0 = base();
        Instant retorno = t0.plus(Duration.ofHours(3));
        long id = solicitudAprobada(e, t0.plus(Duration.ofHours(1)), retorno);

        // Antes del vencimiento no corresponde justificar
        reloj.fijar(t0.plus(Duration.ofHours(2)));
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/justificaciones"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Me demoré\"}")).andExpect(status().isConflict());

        // Vencido: no se puede ampliar, sí justificar
        reloj.fijar(retorno.plus(Duration.ofHours(1)));
        long evidencia = subirEvidencia(e.residente());
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/ampliaciones"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Tarde\",\"retornoNuevo\":\"" + retorno.plus(Duration.ofHours(5)) + "\",\"evidenciaId\":" + evidencia + "}"))
                .andExpect(status().isConflict());
        var just = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/justificaciones"), e.residente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Falla del transporte público\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(conToken(post("/api/v1/permisos/justificaciones/" + just.get("id").asLong() + "/decision"), e.preceptor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprobar\":true,\"comentario\":\"Aceptada\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"));

        // El horario autorizado NO cambió retroactivamente
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), e.residente()))
                .andExpect(jsonPath("$.retornoAutorizado").value(retorno.toString()))
                .andExpect(jsonPath("$.justificaciones[0].estado").value("APROBADA"))
                .andExpect(jsonPath("$.ampliaciones").isEmpty());
    }

    @Test
    void laEvidenciaDebeSerUnaImagenReal() throws Exception {
        var e = escenario();
        mvc.perform(conToken(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "virus.png", "image/png", "no soy una imagen".getBytes())), e.residente()))
                .andExpect(status().isBadRequest());
        mvc.perform(conToken(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "../../x.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})), e.residente())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreOriginal").value(".._.._x.png"));
    }
}
