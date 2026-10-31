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
import pe.upeu.resi.ResiAPI.entity.JustificacionPosterior;
import pe.upeu.resi.ResiAPI.enums.EstadoRevision;

public interface JustificacionPosteriorRepository extends JpaRepository<JustificacionPosterior, Long> {

    List<JustificacionPosterior> findBySolicitudIdOrderByCreadoEnDesc(Long solicitudId);

    boolean existsBySolicitudIdAndEstado(Long solicitudId, EstadoRevision estado);

    Page<JustificacionPosterior> findByEstadoOrderByCreadoEnAsc(EstadoRevision estado, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JustificacionPosterior j where j.id = :id")
    Optional<JustificacionPosterior> findByIdForUpdate(@Param("id") Long id);
}
