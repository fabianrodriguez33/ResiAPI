package pe.upeu.resi.ResiAPI.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.AsistenciaCulto;

public interface AsistenciaCultoRepository extends JpaRepository<AsistenciaCulto, Long> {

    List<AsistenciaCulto> findBySesionIdAndAnuladaFalse(Long sesionId);

    Optional<AsistenciaCulto> findBySesionIdAndResidenteId(Long sesionId, Long residenteId);

    Page<AsistenciaCulto> findByResidenteIdAndAnuladaFalseOrderByRegistradoEnDesc(Long residenteId, Pageable pageable);
}
