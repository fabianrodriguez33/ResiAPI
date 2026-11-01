package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.DiaSinCulto;

public interface DiaSinCultoRepository extends JpaRepository<DiaSinCulto, LocalDate> {

    List<DiaSinCulto> findByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);
}
