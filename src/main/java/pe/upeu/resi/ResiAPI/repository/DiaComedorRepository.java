package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.DiaComedor;

public interface DiaComedorRepository extends JpaRepository<DiaComedor, LocalDate> {
}
