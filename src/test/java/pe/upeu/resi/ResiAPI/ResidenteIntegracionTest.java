package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.service.QrService;

class ResidenteIntegracionTest extends PruebaIntegracion {

    @Autowired
    QrService qr;

    private int asignar(String admin, long residente, long habitacion) throws Exception {
        return mvc.perform(conToken(post("/api/v1/residentes/" + residente + "/asignacion"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"habitacionId\":" + habitacion + "}")).andReturn().getResponse().getStatus();
    }

    @Test
    void laCapacidadMaximaDeLaHabitacionSeRespeta() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long hab = crearHabitacion(admin, 2);
        long r1 = crearResidente(admin), r2 = crearResidente(admin), r3 = crearResidente(admin);
        assertEquals(200, asignar(admin, r1, hab));
        assertEquals(200, asignar(admin, r2, hab));
        assertEquals(409, asignar(admin, r3, hab));
        assertEquals(409, asignar(admin, r1, hab), "repetir la misma habitación no es válido");
    }

    @Test
    void cambiarDeHabitacionLiberaElCupoAnterior() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long a = crearHabitacion(admin, 1), b = crearHabitacion(admin, 1);
        long r1 = crearResidente(admin), r2 = crearResidente(admin);
        assertEquals(200, asignar(admin, r1, a));
        assertEquals(409, asignar(admin, r2, a));
        assertEquals(200, asignar(admin, r1, b));
        assertEquals(200, asignar(admin, r2, a));
    }

    @Test
    void desactivarUnResidenteLiberaSuCupoYBloqueaSuLogin() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long hab = crearHabitacion(admin, 1);
        long r1 = crearResidente(admin);
        String user = ultimoUsuario;
        long r2 = crearResidente(admin);
        assertEquals(200, asignar(admin, r1, hab));
        mvc.perform(conToken(patch("/api/v1/residentes/" + r1), admin).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        assertEquals(200, asignar(admin, r2, hab));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + CLAVE + "\"}")).andExpect(status().isUnauthorized());
        assertEquals(409, asignar(admin, r1, hab), "un residente inactivo no puede recibir habitación");
    }

    @Test
    void asignacionesConcurrentesNoExcedenLaCapacidad() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long hab = crearHabitacion(admin, 2);
        List<Long> residentes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            residentes.add(crearResidente(admin));
        }
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (long r : residentes) {
            Callable<Integer> tarea = () -> asignar(admin, r, hab);
            futuros.add(pool.submit(tarea));
        }
        int exitos = 0;
        for (var f : futuros) {
            if (f.get() == 200) {
                exitos++;
            }
        }
        pool.shutdown();
        assertEquals(2, exitos);
    }

    @Test
    void noSePuedeReducirLaCapacidadPorDebajoDeLosOcupantes() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long hab = crearHabitacion(admin, 4);
        assertEquals(200, asignar(admin, crearResidente(admin), hab));
        assertEquals(200, asignar(admin, crearResidente(admin), hab));
        mvc.perform(conToken(put("/api/v1/habitaciones/" + hab), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"X" + sufijo() + "\",\"capacidad\":1}")).andExpect(status().isConflict());
    }

    @Test
    void validacionDeHabitacionYRoles() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        mvc.perform(conToken(post("/api/v1/habitaciones"), admin).contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"\",\"capacidad\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.numero").exists())
                .andExpect(jsonPath("$.validationErrors.capacidad").exists());
        String preceptor = tokenDeRol(Rol.PRECEPTOR);
        mvc.perform(conToken(post("/api/v1/habitaciones"), preceptor).contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"Z1\",\"capacidad\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/habitaciones"), preceptor)).andExpect(status().isOk());
    }

    @Test
    void elResidenteSoloVeSusDatosYNoPuedeListarResidentes() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        crearResidente(admin);
        String user = ultimoUsuario;
        String token = tokenDe(user);
        mvc.perform(conToken(get("/api/v1/residentes/yo"), token)).andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value(user));
        mvc.perform(conToken(get("/api/v1/residentes"), token)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/auditoria"), token)).andExpect(status().isForbidden());
    }

    @Test
    void elQrNoRevelaDatosPersonalesYUnQrRevocadoDejaDeValer() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        long id = crearResidente(admin);
        String user = ultimoUsuario;
        String token = tokenDe(user);
        String contenido = leer(mvc.perform(conToken(get("/api/v1/residentes/yo/qr"), token)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).get("contenido").asString();
        assertEquals(false, contenido.contains(user), "el QR no debe incluir datos del residente");
        assertEquals(QrService.Estado.VALIDO, qr.resolver(contenido).estado());

        String firmaAlterada = contenido.substring(0, contenido.length() - 2) + (contenido.endsWith("AA") ? "BB" : "AA");
        assertEquals(QrService.Estado.INVALIDO, qr.resolver(firmaAlterada).estado());
        assertEquals(QrService.Estado.INVALIDO, qr.resolver("cualquier-cosa").estado());
        assertEquals(QrService.Estado.INVALIDO, qr.resolver(null).estado());

        String nuevo = leer(mvc.perform(conToken(post("/api/v1/residentes/" + id + "/qr/regenerar"), admin)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).get("contenido").asString();
        assertNotEquals(contenido, nuevo);
        assertEquals(QrService.Estado.REVOCADO, qr.resolver(contenido).estado());
        assertEquals(QrService.Estado.VALIDO, qr.resolver(nuevo).estado());
    }
}
