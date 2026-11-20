package pe.upeu.resi.ResiAPI.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.resi.ResiAPI.entity.ReservaAlimentacion;
import pe.upeu.resi.ResiAPI.entity.SolicitudPermiso;
import pe.upeu.resi.ResiAPI.enums.EstadoReserva;
import pe.upeu.resi.ResiAPI.enums.EstadoSolicitud;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;

public interface ReservaAlimentacionRepository extends JpaRepository<ReservaAlimentacion, Long> {

    /**
     * Bloqueo de la fecha de comida hasta el final de la transacción: reservar, cancelar y (más adelante) cerrar un día se turnan
     * por fecha, así que no se cruzan. Devuelve un texto vacío (la función de PostgreSQL no devuelve valor).
     */
    @Query(value = "select cast(pg_advisory_xact_lock(:clave) as text)", nativeQuery = true)
    String bloquearDia(@Param("clave") long clave);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaAlimentacion r where r.id = :id")
    Optional<ReservaAlimentacion> findByIdForUpdate(@Param("id") Long id);

    /** Fecha de la reserva si es de ese residente (no carga la reserva, para leerla después ya bloqueada y actualizada). */
    @Query("select r.fecha from ReservaAlimentacion r where r.id = :id and r.residenteId = :residenteId")
    Optional<LocalDate> fechaDeSuReserva(@Param("id") Long id, @Param("residenteId") Long residenteId);

    @Query("select count(r) > 0 from ReservaAlimentacion r where r.residenteId = :residenteId and r.fecha = :fecha and r.estado in :estados and r.tipo in :tipos")
    boolean existeEnTipos(@Param("residenteId") Long residenteId, @Param("fecha") LocalDate fecha, @Param("estados") Collection<EstadoReserva> estados,
                          @Param("tipos") Collection<TipoReserva> tipos);

    List<ReservaAlimentacion> findByResidenteIdAndFechaInAndEstadoIn(Long residenteId, Collection<LocalDate> fechas, Collection<EstadoReserva> estados);

    /** Reservas de una fecha (y de un tipo, si se indica) para la lista del pastor, por orden de llegada. */
    @Query("select r from ReservaAlimentacion r where r.fecha = :fecha and r.tipo in :tipos order by r.creadaEn asc, r.id asc")
    List<ReservaAlimentacion> delDia(@Param("fecha") LocalDate fecha, @Param("tipos") Collection<TipoReserva> tipos);

    /** Reservas de una fecha que siguen pendientes, para cancelarlas al cerrar. */
    @Query("select r from ReservaAlimentacion r where r.fecha = :fecha and r.estado = 'PENDIENTE' and r.tipo in :tipos")
    List<ReservaAlimentacion> pendientesDelDia(@Param("fecha") LocalDate fecha, @Param("tipos") Collection<TipoReserva> tipos);

    Page<ReservaAlimentacion> findByResidenteIdOrderByFechaDescIdDesc(Long residenteId, Pageable pageable);

    @Query("select s from SolicitudPermiso s where s.residenteId = :residenteId and s.estado = :estado and s.salidaSolicitada < :fin and s.retornoAutorizado >= :inicio order by s.salidaSolicitada asc")
    List<SolicitudPermiso> permisosQueTocan(@Param("residenteId") Long residenteId, @Param("estado") EstadoSolicitud estado,
                                            @Param("inicio") Instant inicio, @Param("fin") Instant fin);

    /** Permisos aprobados (no anulados, no cancelados) cuya vigencia toca el intervalo [inicio, fin). */
    default List<SolicitudPermiso> permisosAprobadosQueTocan(Long residenteId, Instant inicio, Instant fin) {
        return permisosQueTocan(residenteId, EstadoSolicitud.APROBADO, inicio, fin);
    }
}
