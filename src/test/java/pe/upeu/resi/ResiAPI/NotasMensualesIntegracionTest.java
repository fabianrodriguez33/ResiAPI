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
import pe.upeu.resi.ResiAPI.service.NotasService;

/** Notas mensuales de cultos y limpieza, Excel del pastor y limpieza solo de lunes a viernes. */
class NotasMensualesIntegracionTest extends PruebaIntegracion {

    @Test
    void lasNotasSiguenLosRangosDeLaResidencia() {
        for (long n : new long[] {0, 10}) assertEquals("C", NotasService.notaCultos(n));
        for (long n : new long[] {11, 14}) assertEquals("B", NotasService.notaCultos(n));
        for (long n : new long[] {15, 19}) assertEquals("A", NotasService.notaCultos(n));
        for (long n : new long[] {20, 44}) assertEquals("AD", NotasService.notaCultos(n));
        assertEquals("C", NotasService.notaLimpieza(10.4));
        assertEquals("B", NotasService.notaLimpieza(10.5));
        assertEquals("B", NotasService.notaLimpieza(14.4));
        assertEquals("A", NotasService.notaLimpieza(14.5));
        assertEquals("A", NotasService.notaLimpieza(17.4));
        assertEquals("AD", NotasService.notaLimpieza(17.5));
        assertEquals("AD", NotasService.notaLimpieza(20));
    }

    @Test
    void elResidenteVeSuResumenDelMesYElPastorElReporteYElExcel() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), pastor = tokenDeRol(Rol.PRECEPTOR);
        long id = crearResidente(admin);
        String residente = tokenDe(ultimoUsuario), qr = qrDe(ultimoUsuario);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes, 7, 0);
        long sesion = leer(mvc.perform(conToken(post("/api/v1/cultos/sesiones"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MATUTINO\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(conToken(post("/api/v1/cultos/sesiones/" + sesion + "/asistencias"), pastor).contentType(MediaType.APPLICATION_JSON).content("{\"qr\":\"" + qr + "\"}"))
                .andExpect(status().isCreated());
        String eval = "{\"residenteId\":" + id + ",\"fecha\":\"" + lunes + "\",\"puntajes\":{\"ROPERO\":5,\"CAMA\":3,\"ESCRITORIO\":3,\"BASURA\":3,\"OLORES\":2,\"PISO\":1}}";
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), pastor).contentType(MediaType.APPLICATION_JSON).content(eval)).andExpect(status().isCreated());

        String q = "?anio=" + lunes.getYear() + "&mes=" + lunes.getMonthValue();
        mvc.perform(conToken(get("/api/v1/residentes/yo/resumen-mensual" + q), residente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cultos").value(1)).andExpect(jsonPath("$.notaCultos").value("C"))
                .andExpect(jsonPath("$.evaluaciones").value(1)).andExpect(jsonPath("$.promedioLimpieza").value(17.0)).andExpect(jsonPath("$.notaLimpieza").value("A"));
        // Otro mes sin datos: limpieza sin nota.
        mvc.perform(conToken(get("/api/v1/residentes/yo/resumen-mensual?anio=2030&mes=2"), residente)).andExpect(jsonPath("$.cultos").value(0))
                .andExpect(jsonPath("$.notaLimpieza").doesNotExist());

        mvc.perform(conToken(get("/api/v1/reportes/mensual" + q), pastor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.residenteId == " + id + ")].resumen.notaLimpieza").value("A"));
        var excel = mvc.perform(conToken(get("/api/v1/reportes/mensual.xlsx" + q), pastor)).andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(excel.getContentType().contains("spreadsheetml"));
        assertTrue(excel.getHeader("Content-Disposition").contains("notas-" + lunes.getYear()));
        byte[] datos = excel.getContentAsByteArray();
        assertTrue(datos.length > 1000 && datos[0] == 'P' && datos[1] == 'K', "es un .xlsx (zip)");

        mvc.perform(conToken(get("/api/v1/reportes/mensual" + q), residente)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/reportes/mensual.xlsx" + q), tokenDeRol(Rol.GARITA))).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/reportes/mensual?anio=2026&mes=13"), pastor)).andExpect(status().isBadRequest());
        mvc.perform(conToken(get("/api/v1/residentes/yo/resumen-mensual"), pastor)).andExpect(status().isForbidden());
    }

    @Test
    void laLimpiezaSoloSeEvaluaDeLunesAViernes() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR), pastor = tokenDeRol(Rol.PRECEPTOR);
        long id = crearResidente(admin);
        LocalDate lunes = lunesAleatorio();
        fijarLima(lunes.plusDays(6), 10, 0); // domingo
        String puntajes = "\"puntajes\":{\"ROPERO\":5,\"CAMA\":3,\"ESCRITORIO\":3,\"BASURA\":3,\"OLORES\":2,\"PISO\":1}";
        for (int d : new int[] {5, 6}) {
            mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), pastor).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"residenteId\":" + id + ",\"fecha\":\"" + lunes.plusDays(d) + "\"," + puntajes + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.fecha").exists());
        }
        mvc.perform(conToken(post("/api/v1/limpieza/evaluaciones"), pastor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"residenteId\":" + id + ",\"fecha\":\"" + lunes.plusDays(4) + "\"," + puntajes + "}")).andExpect(status().isCreated());
    }

    @Test
    void elCorreoSoloSeEnviaALosDestinosAutorizados() {
        var envio = new pe.upeu.resi.ResiAPI.service.EnviadorCorreoSmtp(new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public org.springframework.mail.javamail.JavaMailSender getObject() { throw new IllegalStateException(); }
            @Override public org.springframework.mail.javamail.JavaMailSender getObject(Object... args) { throw new IllegalStateException(); }
            @Override public org.springframework.mail.javamail.JavaMailSender getIfAvailable() { return null; }
            @Override public org.springframework.mail.javamail.JavaMailSender getIfUnique() { return null; }
        }, "", "", "61050923@upeu.edu.pe");
        envio.enviarCodigo("61050923@UPEU.edu.pe", "123456");
        var e = org.junit.jupiter.api.Assertions.assertThrows(pe.upeu.resi.ResiAPI.exception.Excepciones.Conflicto.class, () -> envio.enviarCodigo("otro@upeu.edu.pe", "123456"));
        assertTrue(e.getMessage().contains("autorizados"));
    }
}
