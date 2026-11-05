package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.EventoDiaComedor;

public interface EventoDiaComedorRepository extends JpaRepository<EventoDiaComedor, Long> {

    List<EventoDiaComedor> findByFechaOrderByIdAsc(LocalDate fecha);
}
