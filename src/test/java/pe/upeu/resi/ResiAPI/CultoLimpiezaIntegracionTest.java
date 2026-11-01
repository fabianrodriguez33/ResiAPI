package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;

class CultoLimpiezaIntegracionTest extends PruebaIntegracion {

    @org.springframework.beans.factory.annotation.Autowired
    pe.upeu.resi.ResiAPI.repository.RegistroAuditoriaRepository registrosAuditoria;


    private long abrir(String preceptor, String tipo) throws Exception {
        var res = mvc.perform(conToken(post("/api/v1/cultos/sesiones"), preceptor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"" + tipo + "\"}")).andExpect(status().isOk()).andReturn().getResponse();
        return leer(res.getContentAsString()).get("id").asLong();
    }

    private int marcar(String preceptor, long sesion, String qr) throws Exception {
        return mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/asistencias"), preceptor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qr + "\"}")).andReturn().getResponse().getStatus();
    }

    @Test
    void asistenciaConCorteDuplicadosYCierre() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String preceptor = tokenDeRol(Rol.PRECEPTOR);
        crearResidente(admin);
        String u1 = ultimoUsuario;
        long id1 = leer(mvc.perform(conToken(get("/api/v1/residentes/yo"), tokenDe(u1))).andReturn().getResponse().getContentAsString()).get("id").asLong();
        crearResidente(admin);
        String u2 = ultimoUsuario;

        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 6, 2);
        long sesion = abrir(preceptor, "MATUTINO");

        // Dentro del corte (6:05 a. m.)
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/asistencias"), preceptor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qrDe(u1) + "\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.despuesDelCorte").value(false));
        // Duplicado
        assertEquals(409, marcar(preceptor, sesion, qrDe(u1)));

        // Después del corte: se registra igualmente, marcado como posterior al corte (sin sanciones)
        fijarLima(lunes, 6, 10);
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/asistencias"), preceptor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qrDe(u2) + "\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.despuesDelCorte").value(true));

        // QR falso
        assertEquals(400, marcar(preceptor, sesion, "RESIUP1.no-es-un-qr"));

        // El residente no puede registrar asistencia (ni la suya)
        assertEquals(403, marcar(tokenDe(u1), sesion, qrDe(u1)));

        // Presentes y pendientes
        String presentes = mvc.perform(conToken(get("/api/v1/cultos/sesiones/" + sesion + "/presentes"), preceptor)).andReturn().getResponse().getContentAsString();
        assertEquals(2, leer(presentes).size());
        crearResidente(admin);
        String pendientes = mvc.perform(conToken(get("/api/v1/cultos/sesiones/" + sesion + "/pendientes"), preceptor)).andReturn().getResponse().getContentAsString();
        assertTrue(pendientes.contains(ultimoUsuario));

        // Anular (auditado) y volver a registrar
        long asistencia = leer(presentes).get(0).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/cultos/asistencias/" + asistencia + "/anular"), preceptor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Escaneo equivocado\"}")).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/cultos/asistencias/" + asistencia + "/anular"), preceptor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"otra vez\"}")).andExpect(status().isConflict());
        // Se consulta por el id de la asistencia: el listado paginado depende del reloj fijado por otras pruebas.
        assertTrue(registrosAuditoria.findAll().stream().anyMatch(r -> "ASISTENCIA_ANULADA".equals(r.getAccion()) && Long.valueOf(asistencia).equals(r.getEntidadId())));

        // Mi historial
        mvc.perform(conToken(get("/api/v1/residentes/yo/asistencias"), tokenDe(u1))).andExpect(status().isOk());

        // Sesión cerrada: no admite más registros
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/cerrar"), preceptor)).andExpect(status().isOk());
        assertEquals(409, marcar(preceptor, sesion, qrDe(u1)));
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/cerrar"), preceptor)).andExpect(status().isConflict());
        assertTrue(id1 > 0);
    }

    @Test
    void noSeAbrenCultosEnFinDeSemana() throws Exception {
        String preceptor = tokenDeRol(Rol.PRECEPTOR);
        fijarLima(lunesAleatorio().plusDays(5), 6, 0); // sábado
        mvc.perform(conToken(post("/api/v1/cultos/sesiones"), preceptor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void elQrRevocadoNoRegistraAsistencia() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String preceptor = tokenDeRol(Rol.PRECEPTOR);
        long id = crearResidente(admin);
        String viejo = qrDe(ultimoUsuario);
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/qr/regenerar"), admin)).andExpect(status().isOk());
        fijarLima(lunesAleatorio(), 20, 1);
        long sesion = abrir(preceptor, "VESPERTINO");
        assertEquals(409, marcar(preceptor, sesion, viejo));
    }

    @Test
    void limpiezaCalculaTotalYValidaMaximos() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String preceptor = tokenDeRol(Rol.PRECEPTOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario);
        LocalDate miercoles = lunesAleatorio().plusDays(2);
        fijarLima(miercoles, 10, 0);
        String hoy = miercoles.toString();
        String ok = "{\"residenteId\":" + id + ",\"fecha\":\"" + hoy + "\",\"puntajes\":{\"ROPERO\":5,\"CAMA\":3,\"ESCRITORIO\":2,\"BASURA\":3,\"OLORES\":3,\"PISO\":1},\"observacion\":\"Buen estado\"}";
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), preceptor).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(17)).andExpect(jsonPath("$.maximo").value(20));

        // Duplicado en la misma fecha
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), preceptor).contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isConflict());

        // Excede el máximo del ropero (5) y valor negativo
        String malo = "{\"residenteId\":" + id + ",\"fecha\":\"2030-01-01\",\"puntajes\":{\"ROPERO\":6,\"CAMA\":-1,\"ESCRITORIO\":2,\"BASURA\":3,\"OLORES\":3,\"PISO\":1}}";
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), preceptor).contentType(MediaType.APPLICATION_JSON).content(malo)).andExpect(status().isBadRequest());
        String conFecha = malo.replace("2030-01-01", miercoles.minusDays(1).toString());
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), preceptor).contentType(MediaType.APPLICATION_JSON).content(conFecha))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.ROPERO").exists()).andExpect(jsonPath("$.validationErrors.CAMA").exists());

        // Criterio faltante
        String incompleto = "{\"residenteId\":" + id + ",\"fecha\":\"" + miercoles.minusDays(2) + "\",\"puntajes\":{\"ROPERO\":5}}";
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), preceptor).contentType(MediaType.APPLICATION_JSON).content(incompleto))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.PISO").exists());

        // El residente consulta lo suyo; no puede registrar
        mvc.perform(conToken(get("/api/v1/residentes/yo/limpieza"), residente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].total").value(17)).andExpect(jsonPath("$.contenido[0].puntajes.ROPERO").value(5));
        mvc.perform(conToken(get("/api/v1/residentes/yo/limpieza/resumen"), residente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluaciones").value(1)).andExpect(jsonPath("$.promedio").value(17.0));
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), residente).contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isForbidden());
    }
}
