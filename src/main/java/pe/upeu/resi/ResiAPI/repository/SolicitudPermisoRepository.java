package pe.upeu.resi.ResiAPI.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.resi.ResiAPI.entity.SolicitudPermiso;
import pe.upeu.resi.ResiAPI.enums.EstadoSolicitud;

public interface SolicitudPermisoRepository extends JpaRepository<SolicitudPermiso, Long> {

    Page<SolicitudPermiso> findByResidenteIdOrderByCreadoEnDesc(Long residenteId, Pageable pageable);

    Page<SolicitudPermiso> findByEstadoOrderByCreadoEnAsc(EstadoSolicitud estado, Pageable pageable);

    Page<SolicitudPermiso> findAllByOrderByCreadoEnDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudPermiso s where s.id = :id")
    Optional<SolicitudPermiso> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from SolicitudPermiso s where s.residenteId = :residenteId and s.estado = 'APROBADO' and s.retornoAutorizado >= :ahora order by s.salidaSolicitada asc")
    List<SolicitudPermiso> findAprobadasNoVencidas(@Param("residenteId") Long residenteId, @Param("ahora") Instant ahora);

    @Modifying
    @Query("update SolicitudPermiso s set s.estado = 'VENCIDO', s.actualizadoEn = :ahora where s.estado in ('PENDIENTE_APODERADO', 'PENDIENTE_PRECEPTOR') and s.salidaSolicitada < :ahora")
    int marcarVencidas(@Param("ahora") Instant ahora);
}
