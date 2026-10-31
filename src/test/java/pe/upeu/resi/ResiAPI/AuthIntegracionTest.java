package pe.upeu.resi.ResiAPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import pe.upeu.resi.ResiAPI.enums.Rol;

class AuthIntegracionTest extends PruebaIntegracion {

    @Autowired
    JwtEncoder encoder;

    private String login(String user, String pass) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
    }

    @Test
    void loginCorrectoDevuelveTokenRolesYPermisos() throws Exception {
        String user = crearPersonal(Rol.PRECEPTOR);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(user, CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(3600))
                .andExpect(jsonPath("$.roles[0]").value("PRECEPTOR"))
                .andExpect(jsonPath("$.permisos").isNotEmpty());
    }

    @Test
    void loginIncorrectoDevuelve401SinRevelarSiExisteElUsuario() throws Exception {
        String user = crearPersonal(Rol.PRECEPTOR);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(user, "otra-clave-xx")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciales inválidas"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("no_existe_" + sufijo(), CLAVE)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void loginSinCamposDevuelve400ConErroresPorCampo() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.username").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenManipuladoDevuelve401() throws Exception {
        String token = tokenDeRol(Rol.PRECEPTOR);
        String roto = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(conToken(get("/api/v1/auth/me"), roto)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoDevuelve401() throws Exception {
        String user = crearPersonal(Rol.PRECEPTOR);
        Long uid = usuarioService.crear("tmp_" + sufijo(), CLAVE, "Tmp", Rol.GARITA).getId();
        Instant hace = Instant.now().minusSeconds(7200);
        var claims = JwtClaimsSet.builder().subject(user).issuedAt(hace).expiresAt(hace.plusSeconds(60))
                .claim("uid", uid).claim("roles", List.of("GARITA")).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(conToken(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    void rolSinPermisoDevuelve403() throws Exception {
        String garita = tokenDeRol(Rol.GARITA);
        mvc.perform(conToken(get("/api/v1/usuarios"), garita)).andExpect(status().isForbidden());
    }

    @Test
    void administradorGestionaUsuariosYElUsuarioDesactivadoPierdeAcceso() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String user = "garita_" + sufijo();
        var creado = mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"Garita Uno\",\"rol\":\"GARITA\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("GARITA")).andReturn();
        long id = leer(creado.getResponse().getContentAsString()).get("id").asLong();

        String tokenGarita = tokenDe(user);
        mvc.perform(conToken(get("/api/v1/auth/me"), tokenGarita)).andExpect(status().isOk());

        mvc.perform(conToken(patch("/api/v1/usuarios/" + id), admin).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        // El token sigue siendo válido criptográficamente, pero la cuenta está desactivada.
        mvc.perform(conToken(get("/api/v1/auth/me"), tokenGarita)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(user, CLAVE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDuplicadoDevuelve409YClaveCortaDevuelve400() throws Exception {
        String admin = tokenDeRol(Rol.ADMINISTRADOR);
        String user = "dup_" + sufijo();
        String cuerpo = "{\"username\":\"" + user + "\",\"password\":\"" + CLAVE + "\",\"nombreCompleto\":\"X\",\"rol\":\"PRECEPTOR\"}";
        mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
        mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/usuarios"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "2\",\"password\":\"corta\",\"nombreCompleto\":\"X\",\"rol\":\"PRECEPTOR\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void laContrasenaSeGuardaConBCrypt() {
        String user = crearPersonal(Rol.PRECEPTOR);
        var hash = usuarioRepo.findByUsername(user).orElseThrow().getPasswordHash();
        assertFalse(hash.contains(CLAVE));
        assertEquals("$2", hash.substring(0, 2));
    }

    @Autowired
    pe.upeu.resi.ResiAPI.repository.UsuarioRepository usuarioRepo;
}
