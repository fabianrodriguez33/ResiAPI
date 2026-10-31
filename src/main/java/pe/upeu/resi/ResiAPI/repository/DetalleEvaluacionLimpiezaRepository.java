package pe.upeu.resi.ResiAPI.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.DetalleEvaluacionLimpieza;

public interface DetalleEvaluacionLimpiezaRepository extends JpaRepository<DetalleEvaluacionLimpieza, Long> {

    List<DetalleEvaluacionLimpieza> findByEvaluacionIdIn(Collection<Long> ids);
}
