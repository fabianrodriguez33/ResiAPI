package pe.upeu.resi.ResiAPI.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    java.util.List<Usuario> findByRolAndActivoTrue(pe.upeu.resi.ResiAPI.enums.Rol rol);

    boolean existsByUsername(String username);

    boolean existsByDniAndIdNot(String dni, Long id);

    boolean existsByDni(String dni);

    boolean existsByEmailIgnoreCaseAndEmailVerificadoTrueAndIdNot(String email, Long id);

    Page<Usuario> findAllByOrderByNombreCompletoAsc(Pageable pageable);
}
