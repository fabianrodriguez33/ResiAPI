package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.CierreReservas;

public interface CierreReservasRepository extends JpaRepository<CierreReservas, Long> {

    boolean existsByFechaAndGrupo(LocalDate fecha, String grupo);

    List<CierreReservas> findByFecha(LocalDate fecha);
}
