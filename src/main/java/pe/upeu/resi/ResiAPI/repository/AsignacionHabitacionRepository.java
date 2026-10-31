package pe.upeu.resi.ResiAPI.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.AsignacionHabitacion;

public interface AsignacionHabitacionRepository extends JpaRepository<AsignacionHabitacion, Long> {

    Optional<AsignacionHabitacion> findByResidenteIdAndHastaIsNull(Long residenteId);

    long countByHabitacionIdAndHastaIsNull(Long habitacionId);

    List<AsignacionHabitacion> findByHastaIsNull();

    List<AsignacionHabitacion> findByResidenteIdOrderByDesdeDesc(Long residenteId);
}
