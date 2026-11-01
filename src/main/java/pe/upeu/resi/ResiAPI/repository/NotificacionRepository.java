package pe.upeu.resi.ResiAPI.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.resi.ResiAPI.entity.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    Page<Notificacion> findByUsuarioIdOrderByCreadaEnDescIdDesc(Long usuarioId, Pageable pageable);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.usuarioId = :usuario and n.leida = false")
    int marcarTodasLeidas(@Param("usuario") Long usuarioId);
}
