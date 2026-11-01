package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Perfil de padres: aceptan los permisos de sus hijos y envían los de pernoctar fuera. El pastor (preceptor) aprueba al final. */
class PermisoFamiliaIntegracionTest extends PruebaIntegracion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Un residente, su apoderado con cuenta y los tokens necesarios. */
    private record Familia(String admin, String preceptor, String residente, String padre, long residenteId, String usuarioPadre) {
    }

    private Familia familia(boolean conPrivilegio) throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String usuarioResidente = ultimoUsuario;
        String padre = "padre_" + sufijo();
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre " + padre + "\",\"telefono\":\"999111222\",\"parentesco\":\"Padre\",\"principal\":true,\"username\":\"" + padre
                        + "\",\"password\":\"" + CLAVE + "\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.usuario").value(padre));
        if (conPrivilegio) {
            mvc.perform(conToken(patch("/api/v1/residentes/" + id), admin).contentType(MediaType.APPLICATION_JSON).content("{\"puedeSolicitarSolo\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.puedeSolicitarSolo").value(true));
        }
        return new Familia(admin, tokenDeRol(Rol.PRECEPTOR), tokenDe(usuarioResidente), tokenDe(padre), id, padre);
    }

    private Instant ahora() {
        fijarLima(lunesAleatorio(), 10, 0);
        return reloj.instant();
    }

    private static String cuerpo(String tipo, Instant salida, Instant retorno, String extra) {
        return "{\"tipo\":\"" + tipo + "\",\"motivo\":\"Trabajo grupal\",\"destino\":\"La Era\",\"salida\":\"" + salida + "\",\"retorno\":\"" + retorno + "\"" + extra + "}";
    }

    private long solicitar(String token, String cuerpo, String ruta, int estadoEsperado) throws Exception {
        var res = mvc.perform(conToken(post(ruta), token).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().is(estadoEsperado)).andReturn().getResponse();
        return estadoEsperado == 201 ? leer(res.getContentAsString()).get("id").asLong() : -1;
    }

    @Test
    void sinPrivilegioElPermisoPasaPorElPadreYLuegoPorElPastor() throws Exception {
        var f = familia(false);
        Instant t0 = ahora();
        long id = solicitar(f.residente(), cuerpo("LARGO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(5)), ""), "/api/v1/permisos/solicitudes", 201);

        // Queda esperando al padre; el pastor todavía no lo puede aprobar.
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id), f.residente())).andExpect(jsonPath("$.estado").value("PENDIENTE_APODERADO"))
                .andExpect(jsonPath("$.origen").value("RESIDENTE"));
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/decision"), f.preceptor()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isConflict());

        // El padre lo ve en su perfil (con el hijo y el pendiente) y lo acepta.
        mvc.perform(conToken(get("/api/v1/familia/hijos"), f.padre())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residenteId").value(f.residenteId())).andExpect(jsonPath("$[0].pendientes").value(1))
                .andExpect(jsonPath("$[0].puedeSolicitarSolo").value(false));
        var lista = leer(mvc.perform(conToken(get("/api/v1/familia/solicitudes").param("estado", "PENDIENTE_APODERADO"), f.padre())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, lista.get("contenido").size());
        mvc.perform(conToken(post("/api/v1/familia/solicitudes/" + id + "/decision"), f.padre()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE_PRECEPTOR"));
        // No se puede decidir dos veces.
        mvc.perform(conToken(post("/api/v1/familia/solicitudes/" + id + "/decision"), f.padre()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":false}"))
                .andExpect(status().isConflict());

        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/decision"), f.preceptor()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void siElPadreRechazaNuncaLlegaAlPastor() throws Exception {
        var f = familia(false);
        Instant t0 = ahora();
        long id = solicitar(f.residente(), cuerpo("CORTO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(3)), ""), "/api/v1/permisos/solicitudes", 201);
        mvc.perform(conToken(post("/api/v1/familia/solicitudes/" + id + "/decision"), f.padre()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADO"));
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/decision"), f.preceptor()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void conPrivilegioElResidentePideCortoYLargoDirectoAlPastorPeroNoPernoctar() throws Exception {
        var f = familia(true);
        Instant t0 = ahora();
        long corto = solicitar(f.residente(), cuerpo("CORTO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(4)), ""), "/api/v1/permisos/solicitudes", 201);
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + corto), f.residente())).andExpect(jsonPath("$.estado").value("PENDIENTE_PRECEPTOR"))
                .andExpect(jsonPath("$.requiereApoderado").value(false));
        solicitar(f.residente(), cuerpo("LARGO", t0.plus(Duration.ofHours(6)), t0.plus(Duration.ofHours(9)), ""), "/api/v1/permisos/solicitudes", 201);
        // Pernoctar fuera siempre lo envía el padre, aunque el residente tenga el privilegio.
        solicitar(f.residente(), cuerpo("PERNOCTE", t0.plus(Duration.ofDays(2)), t0.plus(Duration.ofDays(5)),
                ",\"responsableNombre\":\"Tía\",\"responsableCelular\":\"+51 968 110 422\""), "/api/v1/permisos/solicitudes", 403);
    }

    @Test
    void elCortoNoPasaDeDosHoras() throws Exception {
        var f = familia(true);
        Instant t0 = ahora();
        solicitar(f.residente(), cuerpo("CORTO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(4)).plusSeconds(60), ""), "/api/v1/permisos/solicitudes", 400);
        solicitar(f.residente(), cuerpo("LARGO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(4)).plusSeconds(60), ""), "/api/v1/permisos/solicitudes", 201);
    }

    @Test
    void elPadreEnviaElPermisoParaPernoctarConLosDatosDeLaCasa() throws Exception {
        var f = familia(false);
        LocalDate dia = lunesAleatorio();
        fijarLima(dia, 10, 0);
        Instant salida = dia.plusDays(4).atTime(14, 0).atZone(LIMA).toInstant();
        Instant retorno = dia.plusDays(7).atTime(10, 30).atZone(LIMA).toInstant();
        String casa = ",\"residenteId\":" + f.residenteId() + ",\"responsableNombre\":\"Haydee Bazán Abanto\",\"responsableCelular\":\"+51 968 110 422\",\"acompanante\":\"\"";

        long id = solicitar(f.padre(), cuerpo("PERNOCTE", salida, retorno, casa), "/api/v1/familia/solicitudes", 201);
        // Lo envía el padre: va directo al pastor, con los datos de la casa y «Nadie» como acompañante por omisión.
        mvc.perform(conToken(get("/api/v1/familia/solicitudes/" + id), f.padre())).andExpect(jsonPath("$.estado").value("PENDIENTE_PRECEPTOR"))
                .andExpect(jsonPath("$.origen").value("APODERADO")).andExpect(jsonPath("$.tipo").value("PERNOCTE"))
                .andExpect(jsonPath("$.responsableNombre").value("Haydee Bazán Abanto")).andExpect(jsonPath("$.responsableCelular").value("+51 968 110 422"))
                .andExpect(jsonPath("$.acompanante").value("Nadie"));
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + id + "/decision"), f.preceptor()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void pernoctarExigeResponsableCelularYVolverOtroDia() throws Exception {
        var f = familia(false);
        LocalDate dia = lunesAleatorio();
        fijarLima(dia, 10, 0);
        Instant salida = dia.plusDays(4).atTime(14, 0).atZone(LIMA).toInstant();
        Instant retorno = dia.plusDays(5).atTime(10, 30).atZone(LIMA).toInstant();
        String hijo = ",\"residenteId\":" + f.residenteId();
        solicitar(f.padre(), cuerpo("PERNOCTE", salida, retorno, hijo), "/api/v1/familia/solicitudes", 400);
        solicitar(f.padre(), cuerpo("PERNOCTE", salida, retorno, hijo + ",\"responsableNombre\":\"Tía\",\"responsableCelular\":\"abc\""), "/api/v1/familia/solicitudes", 400);
        // Salir y volver el mismo día no es pernoctar.
        solicitar(f.padre(), cuerpo("PERNOCTE", salida, salida.plus(Duration.ofHours(5)), hijo + ",\"responsableNombre\":\"Tía\",\"responsableCelular\":\"968110422\""),
                "/api/v1/familia/solicitudes", 400);
        solicitar(f.padre(), cuerpo("PERNOCTE", salida, retorno, hijo + ",\"responsableNombre\":\"Tía\",\"responsableCelular\":\"968110422\",\"acompanante\":\"Su primo\""),
                "/api/v1/familia/solicitudes", 201);
    }

    @Test
    void unPadreSoloVeYDecideSobreSusHijos() throws Exception {
        var mio = familia(false);
        var ajeno = familia(false);
        Instant t0 = ahora();
        long idAjeno = solicitar(ajeno.residente(), cuerpo("LARGO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(5)), ""), "/api/v1/permisos/solicitudes", 201);

        mvc.perform(conToken(get("/api/v1/familia/solicitudes/" + idAjeno), mio.padre())).andExpect(status().isNotFound());
        mvc.perform(conToken(post("/api/v1/familia/solicitudes/" + idAjeno + "/decision"), mio.padre()).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(conToken(post("/api/v1/familia/solicitudes/" + idAjeno + "/cancelar"), mio.padre())).andExpect(status().isNotFound());
        // Tampoco puede enviar un permiso a nombre de un residente que no es su hijo.
        solicitar(mio.padre(), cuerpo("PERNOCTE", t0.plus(Duration.ofDays(2)), t0.plus(Duration.ofDays(4)),
                ",\"residenteId\":" + ajeno.residenteId() + ",\"responsableNombre\":\"Tía\",\"responsableCelular\":\"968110422\""), "/api/v1/familia/solicitudes", 404);
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + idAjeno), mio.padre())).andExpect(status().isForbidden());
        // Su lista no incluye lo ajeno.
        var lista = leer(mvc.perform(conToken(get("/api/v1/familia/solicitudes"), mio.padre())).andReturn().getResponse().getContentAsString());
        assertEquals(0, lista.get("contenido").size());
    }

    @Test
    void unMismoPadreConVariosHijosUsaUnaSolaCuenta() throws Exception {
        var f = familia(false);
        long hermano = crearResidente(f.admin());
        mvc.perform(conToken(post("/api/v1/residentes/" + hermano + "/apoderados"), f.admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre\",\"parentesco\":\"Padre\",\"principal\":true,\"username\":\"" + f.usuarioPadre() + "\"}")).andExpect(status().isCreated());
        // Con la misma cuenta ve a los dos; vincular dos veces al mismo hijo no se permite.
        mvc.perform(conToken(get("/api/v1/familia/hijos"), f.padre())).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(conToken(post("/api/v1/residentes/" + hermano + "/apoderados"), f.admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre\",\"principal\":true,\"username\":\"" + f.usuarioPadre() + "\"}")).andExpect(status().isConflict());
    }

    @Test
    void elPastorVeAsistenciaYLimpiezaDelResidenteAntesDeAprobar() throws Exception {
        var f = familia(true);
        Instant t0 = ahora();
        long id = solicitar(f.residente(), cuerpo("LARGO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(5)), ""), "/api/v1/permisos/solicitudes", 201);
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id + "/resumen-residente"), f.preceptor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.dias").value(30)).andExpect(jsonPath("$.asistencias").value(0)).andExpect(jsonPath("$.cultosRealizados").isNumber())
                .andExpect(jsonPath("$.evaluaciones").value(0)).andExpect(jsonPath("$.promedioLimpieza").doesNotExist());
        // Solo el personal lo consulta.
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id + "/resumen-residente"), f.residente())).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + id + "/resumen-residente"), f.padre())).andExpect(status().isForbidden());
    }

    @Test
    void soloElApoderadoUsaLaRutaDeFamiliaYElResidenteNoEnviaPorEllos() throws Exception {
        var f = familia(false);
        mvc.perform(conToken(get("/api/v1/familia/hijos"), f.residente())).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/familia/hijos"), f.preceptor())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/familia/hijos")).andExpect(status().isUnauthorized());
        // El padre no usa las rutas del residente para enviar permisos.
        Instant t0 = ahora();
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), f.padre()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("LARGO", t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(5)), ""))).andExpect(status().isForbidden());
        assertTrue(true);
    }
}
