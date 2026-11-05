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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.service.PermisoService;

/**
 * Habilitación semanal: dos días de falta sin justificar o un promedio de limpieza menor a 13/20 en la semana anterior
 * inhabilitan la semana siguiente (domingo a sábado). El pastor justifica hasta el lunes.
 */
class HabilitacionIntegracionTest extends PruebaIntegracion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    PermisoService permisoService;

    /** Abre el culto matutino de ese día (se hace a las 7:00) y devuelve el id de la sesión. */
    private long abrirCulto(String pastor, LocalDate dia) throws Exception {
        fijarLima(dia, 7, 0);
        return leer(mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void asistir(String pastor, long sesion, String qr) throws Exception {
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/asistencias"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qr\":\"" + qr + "\"}")).andExpect(status().isCreated());
    }

    /** Semana de cultos de lunes a viernes; el residente asiste solo los primeros [asistidos] días. */
    private void semana(String pastor, LocalDate lunes, String qr, int asistidos) throws Exception {
        for (int i = 0; i < 5; i++) {
            long sesion = abrirCulto(pastor, lunes.plusDays(i));
            if (i < asistidos) {
                asistir(pastor, sesion, qr);
            }
        }
    }

    private String body(String tipo, Instant salida, Instant retorno) {
        return "{\"tipo\":\"" + tipo + "\",\"motivo\":\"Trabajo\",\"destino\":\"La Era\",\"salida\":\"" + salida + "\",\"retorno\":\"" + retorno + "\"}";
    }

    private Instant a(LocalDate dia, int hora) {
        return dia.atTime(hora, 0).atZone(LIMA).toInstant();
    }

    @Test
    void dosFaltasSinJustificarInhabilitanLaSemanaSiguienteYElPastorPuedeJustificarHastaElLunes() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), pastor = tokenDeRol(Rol.PRECEPTOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario), qr = qrDe(ultimoUsuario);
        String padreUsuario = "padre_" + sufijo();
        mvc.perform(conToken(post("/api/v1/residentes/" + id + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre\",\"parentesco\":\"Padre\",\"principal\":true,\"username\":\"" + padreUsuario + "\",\"password\":\"" + CLAVE + "\"}")).andExpect(status().isCreated());
        String padre = tokenDe(padreUsuario);
        LocalDate lunes = lunesAleatorio();
        semana(pastor, lunes, qr, 3); // asiste lunes a miércoles; falta jueves y viernes

        // Domingo siguiente: inhabilitado, con las dos faltas a la vista.
        fijarLima(lunes.plusDays(6), 10, 0);
        mvc.perform(conToken(get("/api/v1/residentes/yo/habilitacion"), residente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(false)).andExpect(jsonPath("$.faltasNoJustificadas").value(2))
                .andExpect(jsonPath("$.hasta").value(lunes.plusDays(12).toString()));
        Instant salida = a(lunes.plusDays(8), 10);
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residente).contentType(MediaType.APPLICATION_JSON).content(body("CORTO", salida, salida.plus(Duration.ofHours(1)))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("inhabilitado")));
        // Tampoco lo que enviaría el padre por él.
        mvc.perform(conToken(post("/api/v1/familia/solicitudes"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"CORTO\",\"motivo\":\"Cita\",\"destino\":\"Clínica\",\"salida\":\"" + salida + "\",\"retorno\":\"" + salida.plus(Duration.ofHours(1)) + "\",\"residenteId\":" + id + "}"))
                .andExpect(status().isConflict());

        // El lunes el pastor justifica una falta (motivo obligatorio): queda con una sola y se habilita.
        fijarLima(lunes.plusDays(7), 9, 0);
        mvc.perform(conToken(get("/api/v1/habilitacion/faltas-por-justificar"), pastor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.residenteId == " + id + ")].fecha").value(org.hamcrest.Matchers.hasItems(lunes.plusDays(3).toString(), lunes.plusDays(4).toString())));
        mvc.perform(conToken(post("/api/v1/habilitacion/faltas/justificar"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"residenteId\":" + id + ",\"fecha\":\"" + lunes.plusDays(3) + "\",\"motivo\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(conToken(post("/api/v1/habilitacion/faltas/justificar"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"residenteId\":" + id + ",\"fecha\":\"" + lunes.plusDays(3) + "\",\"motivo\":\"Estuvo enfermo\"}")).andExpect(status().isNoContent());
        mvc.perform(conToken(post("/api/v1/habilitacion/faltas/justificar"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"residenteId\":" + id + ",\"fecha\":\"" + lunes.plusDays(3) + "\",\"motivo\":\"Otra vez\"}")).andExpect(status().isConflict());
        mvc.perform(conToken(get("/api/v1/residentes/yo/habilitacion"), residente)).andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.faltasNoJustificadas").value(1));
        mvc.perform(conToken(get("/api/v1/notificaciones"), residente)).andExpect(jsonPath("$.contenido[0].titulo").value("Falta justificada"));

        // Pasado el lunes ya no se puede justificar.
        fijarLima(lunes.plusDays(8), 9, 0);
        mvc.perform(conToken(post("/api/v1/habilitacion/faltas/justificar"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"residenteId\":" + id + ",\"fecha\":\"" + lunes.plusDays(4) + "\",\"motivo\":\"Tarde\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("plazo")));
        // Una sola falta no inhabilita: el residente vuelve a poder pedir permisos.
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residente).contentType(MediaType.APPLICATION_JSON).content(body("CORTO", a(lunes.plusDays(9), 10), a(lunes.plusDays(9), 11))))
                .andExpect(status().isCreated());
    }

    @Test
    void unaSemanaSinEvaluacionesDeLimpiezaNoInhabilitaYUnPromedioBajoSi() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), pastor = tokenDeRol(Rol.PRECEPTOR);
        long sin = crearResidente(admin);
        String residenteSin = tokenDe(ultimoUsuario), qrSin = qrDe(ultimoUsuario);
        long bajo = crearResidente(admin);
        String residenteBajo = tokenDe(ultimoUsuario), qrBajo = qrDe(ultimoUsuario);
        long justo = crearResidente(admin);
        String residenteJusto = tokenDe(ultimoUsuario), qrJusto = qrDe(ultimoUsuario);
        LocalDate lunes = lunesAleatorio();
        for (int i = 0; i < 5; i++) {
            long sesion = abrirCulto(pastor, lunes.plusDays(i));
            asistir(pastor, sesion, qrSin);
            asistir(pastor, sesion, qrBajo);
            asistir(pastor, sesion, qrJusto);
        }
        fijarLima(lunes.plusDays(4), 10, 0);
        String malo = "\"puntajes\":{\"ROPERO\":2,\"CAMA\":1,\"ESCRITORIO\":1,\"BASURA\":1,\"OLORES\":1,\"PISO\":1}"; // 7/20
        String justoPuntaje = "\"puntajes\":{\"ROPERO\":4,\"CAMA\":2,\"ESCRITORIO\":2,\"BASURA\":2,\"OLORES\":2,\"PISO\":1}"; // 13/20
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"residenteId\":" + bajo + ",\"fecha\":\"" + lunes.plusDays(4) + "\"," + malo + "}")).andExpect(status().isCreated());
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"residenteId\":" + justo + ",\"fecha\":\"" + lunes.plusDays(4) + "\"," + justoPuntaje + "}")).andExpect(status().isCreated());

        fijarLima(lunes.plusDays(6), 10, 0);
        mvc.perform(conToken(get("/api/v1/residentes/yo/habilitacion"), residenteSin)).andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.evaluacionesLimpieza").value(0));
        mvc.perform(conToken(get("/api/v1/residentes/yo/habilitacion"), residenteJusto)).andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.promedioLimpieza").value(13.0)); // el mínimo de 13/20 alcanza
        mvc.perform(conToken(get("/api/v1/residentes/yo/habilitacion"), residenteBajo)).andExpect(jsonPath("$.habilitado").value(false))
                .andExpect(jsonPath("$.promedioLimpieza").value(7.0)).andExpect(jsonPath("$.motivo").value(org.hamcrest.Matchers.containsString("limpieza")));
    }

    @Test
    void lasSolicitudesSinAprobarSeCancelanSolasYLosAprobadosLosRevisaElPastor() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), pastor = tokenDeRol(Rol.PRECEPTOR);
        // A necesita a su padre; B envía por sí mismo (su permiso queda aprobado).
        long idA = crearResidente(admin);
        String residenteA = tokenDe(ultimoUsuario);
        String padreUsuario = "padre_" + sufijo();
        mvc.perform(conToken(post("/api/v1/residentes/" + idA + "/apoderados"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Padre\",\"parentesco\":\"Padre\",\"principal\":true,\"username\":\"" + padreUsuario + "\",\"password\":\"" + CLAVE + "\"}")).andExpect(status().isCreated());
        String padreA = tokenDe(padreUsuario);
        long idB = crearResidente(admin);
        String residenteB = tokenDe(ultimoUsuario);
        mvc.perform(conToken(patch("/api/v1/residentes/" + idB), admin).contentType(MediaType.APPLICATION_JSON).content("{\"puedeSolicitarSolo\":true}")).andExpect(status().isOk());

        LocalDate lunes = lunesAleatorio();
        Instant salida = a(lunes.plusDays(8), 10); // martes de la semana siguiente
        long solicitudA = 0, solicitudB = 0;
        for (int i = 0; i < 5; i++) {
            abrirCulto(pastor, lunes.plusDays(i)); // hay culto todos los días y ninguno de los dos asiste: 5 faltas
            if (i == 2) { // el miércoles aún están habilitados y piden permiso para la semana siguiente
                solicitudA = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residenteA).contentType(MediaType.APPLICATION_JSON)
                        .content(body("LARGO", salida, salida.plus(Duration.ofHours(4))))).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE_APODERADO"))
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();
                solicitudB = leer(mvc.perform(conToken(post("/api/v1/permisos/solicitudes"), residenteB).contentType(MediaType.APPLICATION_JSON)
                        .content(body("LARGO", salida, salida.plus(Duration.ofHours(4))))).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("APROBADO"))
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();
            }
        }

        // Domingo de la semana de suspensión: el padre ya no puede aprobar y el trabajo programado cancela la solicitud sin aprobar.
        fijarLima(lunes.plusDays(6), 10, 0);
        mvc.perform(conToken(post("/api/v1/familia/solicitudes/" + solicitudA + "/decision"), padreA).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isConflict());
        assertTrue(permisoService.cancelarPendientesDeSuspendidos() >= 1);
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + solicitudA), residenteA)).andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.comentarioDecision").value(org.hamcrest.Matchers.containsString("suspensión")));
        mvc.perform(conToken(get("/api/v1/notificaciones"), residenteA)).andExpect(jsonPath("$.contenido[0].titulo").value("Permiso cancelado por suspensión"));

        // El permiso ya aprobado de B no se toca solo: el pastor lo ve en su lista y decide si lo anula.
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/" + solicitudB), residenteB)).andExpect(jsonPath("$.estado").value("APROBADO"));
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/de-suspendidos"), pastor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + solicitudB + ")]").isNotEmpty());
        mvc.perform(conToken(post("/api/v1/permisos/solicitudes/" + solicitudB + "/anular"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Suspendido por faltas\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADO"));
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/de-suspendidos"), pastor)).andExpect(jsonPath("$[?(@.id == " + solicitudB + ")]").isEmpty());
        mvc.perform(conToken(get("/api/v1/habilitacion/faltas-por-justificar"), residenteA)).andExpect(status().isForbidden());
    }
}
