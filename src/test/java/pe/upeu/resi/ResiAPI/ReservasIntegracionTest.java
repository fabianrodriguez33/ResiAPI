package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import pe.upeu.resi.ResiAPI.entity.DiaComedor;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.entity.AperturaReservas;
import pe.upeu.resi.ResiAPI.repository.AperturaReservasRepository;
import pe.upeu.resi.ResiAPI.repository.DiaComedorRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;

/** Reservas de alimentación del residente: oferta, reservar, cancelar, historial, día cerrado, permisos y solicitudes simultáneas. */
class ReservasIntegracionTest extends PruebaIntegracion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    DiaComedorRepository dias;
    @Autowired
    ResidenteRepository residentes;
    @Autowired
    AperturaReservasRepository aperturas;
    @Autowired
    pe.upeu.resi.ResiAPI.repository.CierreReservasRepository cierresRepo;
    @Autowired
    UsuarioRepository usuarios;

    private final java.util.Map<String, Long> fotos = new java.util.concurrent.ConcurrentHashMap<>();

    /** Foto de la persona del token (se sube una sola vez por token): las reservas del residente la exigen. */
    private long foto(String token) throws Exception {
        Long id = fotos.get(token);
        if (id == null) {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
            id = leer(mvc.perform(multipart("/api/v1/evidencias").file(new MockMultipartFile("archivo", "foto.png", "image/png", png))
                    .header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            fotos.put(token, id);
        }
        return id;
    }

    /** Lo que hace el pastor: abrir las reservas de una comida (ALMUERZO, CENA o DESAYUNO) para una fecha. */
    private synchronized void abrir(LocalDate fecha, String grupo) {
        if (aperturas.existsByFechaAndGrupo(fecha, grupo)) return;
        var a = new AperturaReservas();
        a.setFecha(fecha);
        a.setGrupo(grupo);
        a.setAbiertoPor(usuarios.findAll().get(0).getId());
        a.setAbiertoEn(Instant.now());
        aperturas.saveAndFlush(a);
    }

    private static String grupoDe(String tipo) {
        return tipo.startsWith("CENA") ? "CENA" : tipo.startsWith("DESAYUNO") ? "DESAYUNO" : "ALMUERZO";
    }

    /** Abre lo que se pide en un día: el almuerzo y la cena de hoy, y el desayuno de mañana. */
    private void abrirOferta(LocalDate hoy) {
        abrir(hoy, "ALMUERZO");
        abrir(hoy, "CENA");
        abrir(hoy.plusDays(1), "DESAYUNO");
    }

    private String nuevoResidente() throws Exception {
        crearResidente(tokenDeRol(Rol.ADMINISTRADOR));
        return tokenDe(ultimoUsuario);
    }

    /** Deja todo listo para reservar como lo hace la app: la comida abierta por el pastor y la foto subida. */
    private void preparar(String token, String tipo, LocalDate fecha) throws Exception {
        abrir(fecha, grupoDe(tipo));
        foto(token);
    }

    private ResultActions enviarReserva(String token, String tipo, LocalDate fecha, String extra) throws Exception {
        String completo = (extra.contains("\"curso\"") ? "" : ",\"curso\":\"Clases\"")
                + (extra.contains("evidenciaId") ? "" : ",\"evidenciaId\":" + foto(token)) + extra;
        return mvc.perform(conToken(post("/api/v1/reservas"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"" + tipo + "\",\"fecha\":\"" + fecha + "\",\"motivo\":\"Clases\"" + completo + "}"));
    }

    private ResultActions reservar(String token, String tipo, LocalDate fecha, String extra) throws Exception {
        preparar(token, tipo, fecha);
        return enviarReserva(token, tipo, fecha, extra);
    }

    private ResultActions reservar(String token, String tipo, LocalDate fecha) throws Exception {
        return reservar(token, tipo, fecha, "");
    }

    private long idDe(ResultActions r) throws Exception {
        return leer(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode oferta(String token) throws Exception {
        return leer(mvc.perform(conToken(get("/api/v1/reservas/oferta"), token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode item(JsonNode oferta, String tipo) {
        for (JsonNode i : oferta.get("comidas")) {
            if (i.get("tipo").asText().equals(tipo)) return i;
        }
        return null;
    }

    // ------------------------------------------------------------------ oferta

    @Test
    void laOfertaDependeDelDiaYDeLaHora() throws Exception {
        String res = nuevoResidente();
        LocalDate lunes = lunesAleatorio();

        fijarLima(lunes, 10, 0);
        abrirOferta(lunes);
        var o = oferta(res);
        assertEquals(4, o.get("comidas").size());
        for (String t : new String[] {"CENA_TAPER", "CENA_SEGUNDO_TURNO", "ALMUERZO"}) {
            assertEquals(lunes.toString(), item(o, t).get("fecha").asText());
            assertTrue(item(o, t).get("puedeReservar").asBoolean(), t);
        }
        assertEquals(lunes.plusDays(1).toString(), item(o, "DESAYUNO_FRIO").get("fecha").asText(), "el desayuno es para mañana");
        assertTrue(item(o, "DESAYUNO_FRIO").get("puedeReservar").asBoolean());

        fijarLima(lunes.plusDays(3), 17, 0); // jueves 17:00: cena y desayuno del viernes abiertos; almuerzo vencido
        abrirOferta(lunes.plusDays(3));
        o = oferta(res);
        assertEquals("ABIERTA", item(o, "CENA_TAPER").get("estado").asText());
        assertEquals("VENCIDA", item(o, "ALMUERZO").get("estado").asText());
        assertEquals("ABIERTA", item(o, "DESAYUNO_FRIO").get("estado").asText());
        assertEquals(lunes.plusDays(4).toString(), item(o, "DESAYUNO_FRIO").get("fecha").asText());

        fijarLima(lunes.plusDays(3), 19, 0); // jueves 19:00: todo vencido, también el desayuno del viernes
        o = oferta(res);
        for (String t : new String[] {"CENA_TAPER", "CENA_SEGUNDO_TURNO", "ALMUERZO", "DESAYUNO_FRIO"}) {
            assertEquals("VENCIDA", item(o, t).get("estado").asText(), t);
            assertFalse(item(o, t).get("puedeReservar").asBoolean(), t);
            assertTrue(item(o, t).get("motivoNoDisponible").asText().contains(":"), t);
        }

        fijarLima(lunes.plusDays(4), 10, 0); // viernes: solo almuerzo (no hay cena ni desayuno del sábado)
        o = oferta(res);
        assertEquals(1, o.get("comidas").size());
        assertEquals("ALMUERZO", o.get("comidas").get(0).get("tipo").asText());

        fijarLima(lunes.minusDays(1), 10, 0); // domingo: solo el desayuno del lunes
        o = oferta(res);
        assertEquals(1, o.get("comidas").size());
        assertEquals("DESAYUNO_FRIO", o.get("comidas").get(0).get("tipo").asText());
        assertEquals(lunes.toString(), o.get("comidas").get(0).get("fecha").asText());

        fijarLima(lunes.plusDays(5), 10, 0); // sábado: no hay nada que pedir
        assertEquals(0, oferta(res).get("comidas").size());
    }

    // ------------------------------------------------------------------ reservar

    @Test
    void reservarValidaDiasPlazosYDatosEnElServidor() throws Exception {
        String res = nuevoResidente();
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);

        reservar(res, "ALMUERZO", lunes, ",\"curso\":\"Cálculo II\",\"observacion\":\"Sin ají\"").andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.curso").value("Cálculo II"))
                .andExpect(jsonPath("$.observacion").value("Sin ají")).andExpect(jsonPath("$.advertenciaPermiso").doesNotExist());
        reservar(res, "DESAYUNO_FRIO", lunes.plusDays(1)).andExpect(status().isCreated());

        reservar(res, "ALMUERZO", lunes.plusDays(2)).andExpect(status().isConflict());         // no se reserva con días de anticipación
        reservar(res, "ALMUERZO", lunes.minusDays(1)).andExpect(status().isConflict());        // domingo: no se sirve
        reservar(res, "CENA_TAPER", lunes.plusDays(4)).andExpect(status().isConflict());       // viernes: no hay cena
        reservar(res, "DESAYUNO_FRIO", lunes).andExpect(status().isConflict());                // el desayuno de hoy se pidió ayer
        reservar(res, "CENA_TAPER", lunes.minusDays(7)).andExpect(status().isConflict());      // fecha pasada

        // Validación de datos
        mvc.perform(conToken(post("/api/v1/reservas"), res).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"ALMUERZO\",\"fecha\":\"" + lunes + "\",\"motivo\":\"  \"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.motivo").exists());
        reservar(res, "CENA_TAPER", lunes, ",\"observacion\":\"" + "x".repeat(151) + "\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.observacion").exists());
        reservar(res, "CENA_TAPER", lunes, ",\"curso\":\"" + "x".repeat(101) + "\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.curso").exists());
        mvc.perform(conToken(post("/api/v1/reservas"), res).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CENA_TAPER\",\"fecha\":\"" + lunes + "\",\"motivo\":\"" + "x".repeat(301) + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.motivo").exists());
        // El curso y la foto son obligatorios; el desayuno también lleva curso
        mvc.perform(conToken(post("/api/v1/reservas"), res).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CENA_TAPER\",\"fecha\":\"" + lunes + "\",\"motivo\":\"Clases\",\"evidenciaId\":" + foto(res) + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.curso").exists());
        mvc.perform(conToken(post("/api/v1/reservas"), res).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CENA_TAPER\",\"fecha\":\"" + lunes + "\",\"motivo\":\"Clases\",\"curso\":\"Cálculo\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.evidenciaId").exists());
        reservar(nuevoResidente(), "DESAYUNO_FRIO", lunes.plusDays(1), ",\"curso\":\"Álgebra\"").andExpect(status().isCreated())
                .andExpect(jsonPath("$.curso").value("Álgebra"));
        // Los topes exactos sí se aceptan
        var otro = nuevoResidente();
        mvc.perform(conToken(post("/api/v1/reservas"), otro).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CENA_TAPER\",\"fecha\":\"" + lunes + "\",\"motivo\":\"" + "m".repeat(300) + "\",\"curso\":\"" + "c".repeat(100)
                        + "\",\"observacion\":\"" + "o".repeat(150) + "\",\"evidenciaId\":" + foto(otro) + "}")).andExpect(status().isCreated());
    }

    @Test
    void elCierreEsInclusivoALaHoraExactaYSeRechazaDespues() throws Exception {
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 12, 0);
        reservar(nuevoResidente(), "ALMUERZO", lunes).andExpect(status().isCreated());
        fijarLima(lunes, 12, 1);
        reservar(nuevoResidente(), "ALMUERZO", lunes).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("12:00")));
        fijarLima(lunes, 18, 0);
        reservar(nuevoResidente(), "CENA_SEGUNDO_TURNO", lunes).andExpect(status().isCreated());
        fijarLima(lunes, 18, 1);
        reservar(nuevoResidente(), "CENA_SEGUNDO_TURNO", lunes).andExpect(status().isConflict());
        // El desayuno de mañana abre a las 00:00 de hoy
        fijarLima(lunes, 0, 0);
        reservar(nuevoResidente(), "DESAYUNO_FRIO", lunes.plusDays(1)).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ apertura por el pastor

    @Test
    void lasReservasNoAbrenSolasElPastorLasAbreYSoloEnLosDiasQueSeSirve() throws Exception {
        String res = nuevoResidente();
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        // La base de pruebas conserva lo de corridas anteriores: se parte de un día sin aperturas ni cierres.
        aperturas.deleteAll(aperturas.findByFecha(lunes));
        cierresRepo.deleteAll(cierresRepo.findByFecha(lunes));
        long foto = foto(res);
        String cuerpo = "{\"tipo\":\"ALMUERZO\",\"fecha\":\"" + lunes + "\",\"motivo\":\"Clases\",\"curso\":\"Cálculo\",\"evidenciaId\":" + foto + "}";
        String abrirAlmuerzo = "{\"fecha\":\"" + lunes + "\",\"grupo\":\"ALMUERZO\"}";

        // Sin que el pastor abra, no se reserva aunque sea lunes y haya horario
        assertEquals("SIN_ABRIR", item(oferta(res), "ALMUERZO").get("estado").asText());
        assertFalse(item(oferta(res), "ALMUERZO").get("puedeReservar").asBoolean());
        mvc.perform(conToken(post("/api/v1/reservas"), res).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isConflict());

        // Solo el pastor abre; abrir dos veces no falla
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), res).contentType(MediaType.APPLICATION_JSON).content(abrirAlmuerzo)).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON).content(abrirAlmuerzo)).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON).content(abrirAlmuerzo)).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/reservas/aperturas").param("fecha", lunes.toString()), pastor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ALMUERZO"));
        assertEquals("ABIERTA", item(oferta(res), "ALMUERZO").get("estado").asText());
        assertEquals("SIN_ABRIR", item(oferta(res), "CENA_TAPER").get("estado").asText(), "abrir el almuerzo no abre la cena");
        mvc.perform(conToken(post("/api/v1/reservas"), res).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());

        // No se abren fines de semana ni cenas del viernes ni días pasados
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + lunes.plusDays(5) + "\",\"grupo\":\"ALMUERZO\"}")).andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + lunes.plusDays(4) + "\",\"grupo\":\"CENA\"}")).andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + lunes.minusDays(7) + "\",\"grupo\":\"ALMUERZO\"}")).andExpect(status().isConflict());

        // Una comida cerrada no se vuelve a abrir
        mvc.perform(conToken(post("/api/v1/reservas/cerrar"), pastor).contentType(MediaType.APPLICATION_JSON).content(abrirAlmuerzo)).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/reservas/abrir"), pastor).contentType(MediaType.APPLICATION_JSON).content(abrirAlmuerzo)).andExpect(status().isConflict());
        assertEquals("CERRADAS", item(oferta(nuevoResidente()), "ALMUERZO").get("estado").asText());
    }

    // ------------------------------------------------------------------ exclusividad y cancelación

    @Test
    void lasCenasSonExcluyentesYTrasCancelarSePuedeVolverAReservar() throws Exception {
        String res = nuevoResidente();
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);

        long taper = idDe(reservar(res, "CENA_TAPER", lunes).andExpect(status().isCreated()));
        reservar(res, "CENA_SEGUNDO_TURNO", lunes).andExpect(status().isConflict());
        reservar(res, "CENA_TAPER", lunes).andExpect(status().isConflict());

        mvc.perform(conToken(post("/api/v1/reservas/" + taper + "/cancelar"), res)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA_RESIDENTE")).andExpect(jsonPath("$.canceladaEn").exists());
        mvc.perform(conToken(post("/api/v1/reservas/" + taper + "/cancelar"), res)).andExpect(status().isConflict()); // ya cancelada

        long segundo = idDe(reservar(res, "CENA_SEGUNDO_TURNO", lunes).andExpect(status().isCreated()));
        reservar(res, "CENA_TAPER", lunes).andExpect(status().isConflict()); // ahora la activa es la de segundo turno
        mvc.perform(conToken(post("/api/v1/reservas/" + segundo + "/cancelar"), res)).andExpect(status().isOk());
        reservar(res, "CENA_TAPER", lunes).andExpect(status().isCreated());

        // El historial conserva las canceladas y la activa
        var historial = leer(mvc.perform(conToken(get("/api/v1/reservas/mias"), res)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(3, historial.get("totalElementos").asInt());
        List<String> estados = new ArrayList<>();
        historial.get("contenido").forEach(r -> estados.add(r.get("estado").asText()));
        Collections.sort(estados);
        assertEquals(List.of("CANCELADA_RESIDENTE", "CANCELADA_RESIDENTE", "PENDIENTE"), estados);
    }

    @Test
    void soloSeCancelaMientrasElPlazoSigueAbierto() throws Exception {
        String res = nuevoResidente();
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 11, 0);
        long almuerzo = idDe(reservar(res, "ALMUERZO", lunes).andExpect(status().isCreated()));
        long otra = idDe(reservar(res, "CENA_TAPER", lunes).andExpect(status().isCreated()));
        fijarLima(lunes, 12, 0); // el almuerzo todavía se puede cancelar a las 12:00 exactas
        mvc.perform(conToken(post("/api/v1/reservas/" + almuerzo + "/cancelar"), res)).andExpect(status().isOk());
        fijarLima(lunes, 18, 1); // la cena ya no
        mvc.perform(conToken(post("/api/v1/reservas/" + otra + "/cancelar"), res)).andExpect(status().isConflict());
        // y la reserva sigue activa y visible en el historial
        mvc.perform(conToken(get("/api/v1/reservas/mias"), res)).andExpect(jsonPath("$.contenido[?(@.id == " + otra + ")].estado").value("PENDIENTE"));
    }

    // ------------------------------------------------------------------ cada residente, lo suyo

    @Test
    void cadaResidenteSoloVeCreaYCancelaLasSuyasYLosOtrosRolesNoEntran() throws Exception {
        String a = nuevoResidente(), b = nuevoResidente();
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        long deA = idDe(reservar(a, "ALMUERZO", lunes).andExpect(status().isCreated()));

        mvc.perform(conToken(post("/api/v1/reservas/" + deA + "/cancelar"), b)).andExpect(status().isNotFound());
        mvc.perform(conToken(get("/api/v1/reservas/mias"), b)).andExpect(jsonPath("$.totalElementos").value(0));
        assertTrue(item(oferta(b), "ALMUERZO").get("reservaActiva").isNull(), "la reserva de A no aparece en la oferta de B");
        reservar(b, "ALMUERZO", lunes).andExpect(status().isCreated()); // B puede reservar lo suyo
        mvc.perform(conToken(post("/api/v1/reservas/" + deA + "/cancelar"), a)).andExpect(status().isOk());

        for (Rol rol : new Rol[] {Rol.PRECEPTOR, Rol.ADMINISTRADOR, Rol.GARITA, Rol.APODERADO}) {
            String token = tokenDeRol(rol);
            mvc.perform(conToken(get("/api/v1/reservas/oferta"), token)).andExpect(status().isForbidden());
            mvc.perform(conToken(get("/api/v1/reservas/mias"), token)).andExpect(status().isForbidden());
            reservar(token, "ALMUERZO", lunes).andExpect(status().isForbidden());
            mvc.perform(conToken(post("/api/v1/reservas/" + deA + "/cancelar"), token)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/reservas/oferta")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ día cerrado y residente inactivo

    @Test
    void enUnDiaCerradoNoSeReservaYElResidenteVeElMotivo() throws Exception {
        long residenteId = crearResidente(tokenDeRol(Rol.ADMINISTRADOR));
        String res = tokenDe(ultimoUsuario);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        var dia = new DiaComedor();
        dia.setFecha(lunes);
        dia.setCerrado(true);
        dia.setMotivo("Feriado nacional");
        dia.setActualizadoPor(residentes.findById(residenteId).orElseThrow().getUsuarioId()); // referencia a un usuario real
        dia.setActualizadoEn(Instant.now());
        dias.saveAndFlush(dia);
        abrirOferta(lunes);

        reservar(res, "ALMUERZO", lunes).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Feriado nacional")));
        var o = oferta(res);
        assertEquals("DIA_CERRADO", item(o, "ALMUERZO").get("estado").asText());
        assertFalse(item(o, "ALMUERZO").get("puedeReservar").asBoolean());
        assertEquals("Feriado nacional", item(o, "ALMUERZO").get("motivoCierre").asText());
        assertEquals("ABIERTA", item(o, "DESAYUNO_FRIO").get("estado").asText(), "el desayuno de mañana es de otro día");
        reservar(res, "DESAYUNO_FRIO", lunes.plusDays(1)).andExpect(status().isCreated());

        dia.setCerrado(false);
        dias.saveAndFlush(dia);
        reservar(res, "ALMUERZO", lunes).andExpect(status().isCreated());
    }

    @Test
    void unResidenteInactivoNoPuedeRegistrarReservasNuevas() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String res = tokenDe(ultimoUsuario);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        reservar(res, "ALMUERZO", lunes).andExpect(status().isCreated());
        var r = residentes.findById(id).orElseThrow();
        r.setActivo(false);
        residentes.saveAndFlush(r);
        reservar(res, "CENA_TAPER", lunes).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no está activo")));
    }

    // ------------------------------------------------------------------ permisos

    @Test
    void unPermisoAprobadoSoloAdvierteYNoBloqueaLaReserva() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String res = tokenDe(ultimoUsuario);
        mvc.perform(conToken(patch("/api/v1/residentes/" + id), admin).contentType(MediaType.APPLICATION_JSON).content("{\"puedeSolicitarSolo\":true}")).andExpect(status().isOk());
        String sinPermiso = nuevoResidente();

        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 9, 0);
        Instant salida = lunes.atTime(10, 0).atZone(LIMA).toInstant(), retorno = lunes.atTime(14, 0).atZone(LIMA).toInstant();
        long permiso = idDe(mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), res).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"LARGO\",\"motivo\":\"Trabajo\",\"destino\":\"La Era\",\"salida\":\"" + salida + "\",\"retorno\":\"" + retorno + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE_PRECEPTOR")));
        aprobarComoPastor(tokenDeRol(Rol.PRECEPTOR), permiso);

        // Reserva sin bloqueo y con la advertencia en la respuesta
        reservar(res, "ALMUERZO", lunes).andExpect(status().isCreated()).andExpect(jsonPath("$.advertenciaPermiso.tipo").value("LARGO"))
                .andExpect(jsonPath("$.advertenciaPermiso.mensaje").exists());
        assertNotNull(item(oferta(res), "CENA_TAPER").get("advertenciaPermiso").get("tipo"), "la oferta también la muestra");
        // El desayuno de mañana no coincide con el permiso: sin advertencia
        reservar(res, "DESAYUNO_FRIO", lunes.plusDays(1)).andExpect(status().isCreated()).andExpect(jsonPath("$.advertenciaPermiso").doesNotExist());
        // Quien no tiene permiso, sin advertencia
        reservar(sinPermiso, "ALMUERZO", lunes).andExpect(status().isCreated()).andExpect(jsonPath("$.advertenciaPermiso").doesNotExist());
    }

    // ------------------------------------------------------------------ solicitudes simultáneas

    @Test
    void elPastorApruebaORechazaLasReservasPendientes() throws Exception {
        String res = nuevoResidente();
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        long almuerzo = idDe(reservar(res, "ALMUERZO", lunes).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE")));
        long cena = idDe(reservar(res, "CENA_TAPER", lunes).andExpect(status().isCreated()));
        // Una pendiente ya ocupa el cupo: no se puede pedir otra igual.
        reservar(res, "ALMUERZO", lunes).andExpect(status().isConflict());
        // El pastor ve la lista del día con quién pidió; el residente no puede decidir.
        mvc.perform(conToken(get("/api/v1/reservas/del-dia").param("fecha", lunes.toString()).param("tipo", "ALMUERZO"), pastor))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + almuerzo + ")].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[?(@.id == " + almuerzo + ")].residenteNombre").exists());
        mvc.perform(conToken(post("/api/v1/reservas/" + almuerzo + "/decision"), res).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isForbidden());
        // Aprobar la deja RESERVADA; rechazar exige motivo y libera el cupo.
        mvc.perform(conToken(post("/api/v1/reservas/" + almuerzo + "/decision"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESERVADA"));
        mvc.perform(conToken(post("/api/v1/reservas/" + cena + "/decision"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(conToken(post("/api/v1/reservas/" + cena + "/decision"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aprobar\":false,\"comentario\":\"Sin cupo\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"));
        mvc.perform(conToken(post("/api/v1/reservas/" + cena + "/decision"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isConflict());
        reservar(res, "CENA_TAPER", lunes).andExpect(status().isCreated());
        mvc.perform(conToken(get("/api/v1/notificaciones"), res)).andExpect(status().isOk());
    }

    private List<Integer> enParalelo(String token, String tipoA, String tipoB, LocalDate fecha) throws Exception {
        preparar(token, tipoA, fecha);
        preparar(token, tipoB, fecha);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2), salida = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (String tipo : new String[] {tipoA, tipoB}) {
            futuros.add(pool.submit(() -> {
                listos.countDown();
                salida.await();
                return enviarReserva(token, tipo, fecha, "").andReturn().getResponse().getStatus();
            }));
        }
        listos.await();
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (var f : futuros) estados.add(f.get());
        pool.shutdown();
        Collections.sort(estados);
        return estados;
    }

    @Test
    void doSolicitudesSimultaneasNuncaDejanDosCenasNiLaMismaReservaDuplicada() throws Exception {
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 10, 0);
        for (int vuelta = 0; vuelta < 3; vuelta++) {
            String res = nuevoResidente();
            // Cena en táper y cena de segundo turno a la vez: solo una queda activa
            assertEquals(List.of(201, 409), enParalelo(res, "CENA_TAPER", "CENA_SEGUNDO_TURNO", lunes));
            var h = leer(mvc.perform(conToken(get("/api/v1/reservas/mias"), res)).andReturn().getResponse().getContentAsString());
            assertEquals(1, h.get("totalElementos").asInt());
            // La misma comida dos veces a la vez: una sola
            String otro = nuevoResidente();
            assertEquals(List.of(201, 409), enParalelo(otro, "ALMUERZO", "ALMUERZO", lunes));
            assertEquals(1, leer(mvc.perform(conToken(get("/api/v1/reservas/mias"), otro)).andReturn().getResponse().getContentAsString()).get("totalElementos").asInt());
        }
    }
}
