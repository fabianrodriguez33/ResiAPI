package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.EvaluacionLimpieza;

public interface EvaluacionLimpiezaRepository extends JpaRepository<EvaluacionLimpieza, Long> {

    boolean existsByResidenteIdAndFecha(Long residenteId, LocalDate fecha);

    /** Promedio de limpieza (sobre 20) por residente en el periodo: filas [residenteId, promedio]. */
    @org.springframework.data.jpa.repository.Query("select e.residenteId, avg(e.total) from EvaluacionLimpieza e where e.fecha between :desde and :hasta group by e.residenteId")
    java.util.List<Object[]> promedioPorResidente(@org.springframework.data.repository.query.Param("desde") LocalDate desde,
                                                  @org.springframework.data.repository.query.Param("hasta") LocalDate hasta);

    java.util.List<EvaluacionLimpieza> findByResidenteIdInAndFecha(java.util.Collection<Long> residenteIds, LocalDate fecha);

    java.util.List<EvaluacionLimpieza> findByHabitacionIdAndFecha(Long habitacionId, LocalDate fecha);

    java.util.List<EvaluacionLimpieza> findByFecha(LocalDate fecha);

    Page<EvaluacionLimpieza> findByResidenteIdOrderByFechaDesc(Long residenteId, Pageable pageable);

    /** Una fila: {cantidad, promedio del total}; el promedio es null si no hay evaluaciones. */
    @org.springframework.data.jpa.repository.Query("select count(e), avg(e.total) from EvaluacionLimpieza e where e.residenteId = :residenteId and e.fecha between :desde and :hasta")
    java.util.List<Object[]> estadisticas(@org.springframework.data.repository.query.Param("residenteId") Long residenteId,
                                          @org.springframework.data.repository.query.Param("desde") LocalDate desde,
                                          @org.springframework.data.repository.query.Param("hasta") LocalDate hasta);
}
