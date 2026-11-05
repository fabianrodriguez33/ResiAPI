package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.JustificacionFalta;

public interface JustificacionFaltaRepository extends JpaRepository<JustificacionFalta, Long> {

    List<JustificacionFalta> findByResidenteIdAndFechaBetween(Long residenteId, LocalDate desde, LocalDate hasta);

    boolean existsByResidenteIdAndFecha(Long residenteId, LocalDate fecha);
}
