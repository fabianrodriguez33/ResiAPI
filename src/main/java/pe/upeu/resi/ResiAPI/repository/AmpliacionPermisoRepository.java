package pe.upeu.resi.ResiAPI.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.resi.ResiAPI.entity.AmpliacionPermiso;
import pe.upeu.resi.ResiAPI.enums.EstadoRevision;

public interface AmpliacionPermisoRepository extends JpaRepository<AmpliacionPermiso, Long> {

    List<AmpliacionPermiso> findBySolicitudIdOrderByCreadoEnDesc(Long solicitudId);

    boolean existsBySolicitudIdAndEstado(Long solicitudId, EstadoRevision estado);

    Page<AmpliacionPermiso> findByEstadoOrderByCreadoEnAsc(EstadoRevision estado, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AmpliacionPermiso a where a.id = :id")
    Optional<AmpliacionPermiso> findByIdForUpdate(@Param("id") Long id);
}
