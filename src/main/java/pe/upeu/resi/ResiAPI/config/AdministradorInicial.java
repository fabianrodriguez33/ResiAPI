package pe.upeu.resi.ResiAPI.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.service.UsuarioService;

/** Crea el primer administrador a partir de variables de entorno, sólo si no existe ninguno. No hay contraseña por defecto. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdministradorInicial implements ApplicationRunner {

    private final AppProperties props;
    private final UsuarioRepository usuarios;
    private final UsuarioService servicio;

    @Override
    public void run(ApplicationArguments args) {
        String usuario = props.admin().usuario();
        String clave = props.admin().clave();
        boolean hayAdmin = usuarios.findAll().stream().anyMatch(u -> u.getRol() == Rol.ADMINISTRADOR);
        if (hayAdmin) {
            return;
        }
        if (usuario == null || usuario.isBlank() || clave == null || clave.length() < 8) {
            log.warn("No existe administrador. Defina ADMIN_USUARIO y ADMIN_CLAVE (mínimo 8 caracteres) para crear el inicial.");
            return;
        }
        servicio.crear(usuario, clave, "Administrador", Rol.ADMINISTRADOR);
        log.info("Administrador inicial '{}' creado.", usuario);
    }
}
