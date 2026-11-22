package pe.upeu.resi.ResiAPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import pe.upeu.resi.ResiAPI.enums.Rol;

/** Contadores del inicio del pastor: residentes presentes hoy y permisos aprobados hoy. */
class InicioPastorIntegracionTest extends PruebaIntegracion {

    @Test
    void elPastorVeLosContadoresDeHoyYElResidenteNo() throws Exception {
        String pastor = tokenDeRol(Rol.PRECEPTOR);
        mvc.perform(conToken(get("/api/v1/cultos/presentes-hoy"), pastor)).andExpect(status().isOk()).andExpect(jsonPath("$.residentes").isNumber());
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/aprobados-hoy"), pastor)).andExpect(status().isOk()).andExpect(jsonPath("$.aprobados").isNumber());

        crearResidente(tokenDeRol(Rol.ADMINISTRADOR));
        String residente = tokenDe(ultimoUsuario);
        mvc.perform(conToken(get("/api/v1/cultos/presentes-hoy"), residente)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/permisos/solicitudes/aprobados-hoy"), residente)).andExpect(status().isForbidden());
    }
}
