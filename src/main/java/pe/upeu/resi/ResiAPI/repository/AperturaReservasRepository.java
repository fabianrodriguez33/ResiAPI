package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.AperturaReservas;

public interface AperturaReservasRepository extends JpaRepository<AperturaReservas, Long> {

    boolean existsByFechaAndGrupo(LocalDate fecha, String grupo);

    List<AperturaReservas> findByFecha(LocalDate fecha);
}
