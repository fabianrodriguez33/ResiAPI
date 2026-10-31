package pe.upeu.resi.ResiAPI.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.resi.ResiAPI.entity.MovimientoGarita;
import pe.upeu.resi.ResiAPI.enums.TipoMovimiento;

public interface MovimientoGaritaRepository extends JpaRepository<MovimientoGarita, Long> {

    Optional<MovimientoGarita> findFirstByResidenteIdAndAnuladoFalseOrderByRegistradoEnDescIdDesc(Long residenteId);

    boolean existsBySolicitudIdAndTipoAndAnuladoFalse(Long solicitudId, TipoMovimiento tipo);

    Page<MovimientoGarita> findAllByOrderByRegistradoEnDesc(Pageable pageable);

    Page<MovimientoGarita> findByResidenteIdOrderByRegistradoEnDesc(Long residenteId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MovimientoGarita m where m.id = :id")
    Optional<MovimientoGarita> findByIdForUpdate(@Param("id") Long id);
}
