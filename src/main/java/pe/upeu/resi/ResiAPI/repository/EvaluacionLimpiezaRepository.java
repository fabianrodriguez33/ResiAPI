package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.EvaluacionLimpieza;

public interface EvaluacionLimpiezaRepository extends JpaRepository<EvaluacionLimpieza, Long> {

    boolean existsByResidenteIdAndFecha(Long residenteId, LocalDate fecha);

    java.util.List<EvaluacionLimpieza> findByResidenteIdInAndFecha(java.util.Collection<Long> residenteIds, LocalDate fecha);

    java.util.List<EvaluacionLimpieza> findByHabitacionIdAndFecha(Long habitacionId, LocalDate fecha);

    Page<EvaluacionLimpieza> findByResidenteIdOrderByFechaDesc(Long residenteId, Pageable pageable);
}
