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

    Page<SolicitudPermiso> findByResidenteIdInOrderByCreadoEnDesc(java.util.Collection<Long> residenteIds, Pageable pageable);

    Page<SolicitudPermiso> findByResidenteIdInAndEstadoOrderByCreadoEnDesc(java.util.Collection<Long> residenteIds, EstadoSolicitud estado, Pageable pageable);

    long countByResidenteIdInAndEstado(java.util.Collection<Long> residenteIds, EstadoSolicitud estado);

    Page<SolicitudPermiso> findByEstadoOrderByCreadoEnDesc(EstadoSolicitud estado, Pageable pageable);

    java.util.List<SolicitudPermiso> findByEstado(EstadoSolicitud estado);

    Page<SolicitudPermiso> findAllByOrderByCreadoEnDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudPermiso s where s.id = :id")
    Optional<SolicitudPermiso> findByIdForUpdate(@Param("id") Long id);

    Optional<SolicitudPermiso> findByCreadoPorAndClaveCliente(Long creadoPor, String claveCliente);

    @Query("select s from SolicitudPermiso s where s.residenteId = :residenteId and s.estado = 'APROBADO' and s.retornoAutorizado >= :ahora order by s.salidaSolicitada asc")
    List<SolicitudPermiso> findAprobadasNoVencidas(@Param("residenteId") Long residenteId, @Param("ahora") Instant ahora);

    @Modifying
    @Query("update SolicitudPermiso s set s.estado = 'VENCIDO', s.actualizadoEn = :ahora where s.estado in ('PENDIENTE_APODERADO', 'PENDIENTE_PRECEPTOR') and s.salidaSolicitada < :ahora")
    int marcarVencidas(@Param("ahora") Instant ahora);

    @Query("select count(s) > 0 from SolicitudPermiso s where s.residenteId = :residenteId and s.estado in ('PENDIENTE_APODERADO', 'PENDIENTE_PRECEPTOR', 'APROBADO') and s.salidaSolicitada < :fin and s.retornoAutorizado > :inicio")
    boolean existeSolapada(@Param("residenteId") Long residenteId, @Param("inicio") Instant inicio, @Param("fin") Instant fin);
}
