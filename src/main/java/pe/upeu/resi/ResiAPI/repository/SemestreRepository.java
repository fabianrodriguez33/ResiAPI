package pe.upeu.resi.ResiAPI.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.Semestre;

public interface SemestreRepository extends JpaRepository<Semestre, Long> {

    Optional<Semestre> findByActivoTrue();

    boolean existsByNombreIgnoreCase(String nombre);

    List<Semestre> findAllByOrderByInicioDesc();
}
