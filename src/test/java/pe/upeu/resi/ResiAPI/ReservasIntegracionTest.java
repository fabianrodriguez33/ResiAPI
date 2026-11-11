package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;
import pe.upeu.resi.ResiAPI.entity.DiaComedor;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.repository.DiaComedorRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;

/** Reservas de alimentación del residente: oferta, reservar, cancelar, historial, día cerrado, permisos y solicitudes simultáneas. */
class ReservasIntegracionTest extends PruebaIntegracion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    DiaComedorRepository dias;
    @Autowired
    ResidenteRepository residentes;

    private String nuevoResidente() throws Exception {
        crearResidente(tokenDeRol(Rol.ADMINISTRADOR));
        return tokenDe(ultimoUsuario);
    }

    private ResultActions reservar(String token, String tipo, LocalDate fecha, String extra) throws Exception {
        return mvc.perform(conToken(post("/api/v1/reservas"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"" + tipo + "\",\"fecha\":\"" + fecha + "\",\"motivo\":\"Clases\"" + extra + "}"));
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
        var o = oferta(res);
        assertEquals(4, o.get("comidas").size());
        for (String t : new String[] {"CENA_TAPER", "CENA_SEGUNDO_TURNO", "ALMUERZO"}) {
            assertEquals(lunes.toString(), item(o, t).get("fecha").asText());
            assertTrue(item(o, t).get("puedeReservar").asBoolean(), t);
        }
        assertEquals(lunes.plusDays(1).toString(), item(o, "DESAYUNO_FRIO").get("fecha").asText(), "el desayuno es para mañana");
        assertTrue(item(o, "DESAYUNO_FRIO").get("puedeReservar").asBoolean());

        fijarLima(lunes.plusDays(3), 17, 0); // jueves 17:00: cena y desayuno del viernes abiertos; almuerzo vencido
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
                .andExpect(jsonPath("$.estado").value("RESERVADA")).andExpect(jsonPath("$.curso").value("Cálculo II"))
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
        reservar(res, "DESAYUNO_FRIO", lunes.plusDays(1), ",\"curso\":\"Álgebra\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.curso").exists());
        // Los topes exactos sí se aceptan
        var otro = nuevoResidente();
        mvc.perform(conToken(post("/api/v1/reservas"), otro).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CENA_TAPER\",\"fecha\":\"" + lunes + "\",\"motivo\":\"" + "m".repeat(300) + "\",\"curso\":\"" + "c".repeat(100)
                        + "\",\"observacion\":\"" + "o".repeat(150) + "\"}")).andExpect(status().isCreated());
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
        assertEquals(List.of("CANCELADA_RESIDENTE", "CANCELADA_RESIDENTE", "RESERVADA"), estados);
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
        mvc.perform(conToken(get("/api/v1/reservas/mias"), res)).andExpect(jsonPath("$.contenido[?(@.id == " + otra + ")].estado").value("RESERVADA"));
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
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), res).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"LARGO\",\"motivo\":\"Trabajo\",\"destino\":\"La Era\",\"salida\":\"" + salida + "\",\"retorno\":\"" + retorno + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("APROBADO"));

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

    private List<Integer> enParalelo(String token, String tipoA, String tipoB, LocalDate fecha) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2), salida = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (String tipo : new String[] {tipoA, tipoB}) {
            futuros.add(pool.submit(() -> {
                listos.countDown();
                salida.await();
                return reservar(token, tipo, fecha).andReturn().getResponse().getStatus();
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
