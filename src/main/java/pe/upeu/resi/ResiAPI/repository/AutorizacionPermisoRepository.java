package pe.upeu.resi.ResiAPI.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.resi.ResiAPI.entity.AutorizacionPermiso;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;

public interface AutorizacionPermisoRepository extends JpaRepository<AutorizacionPermiso, Long> {

    List<AutorizacionPermiso> findByResidenteIdOrderByIdDesc(Long residenteId);

    /** ¿Tiene una autorización no revocada y vigente en esa fecha para ese tipo? */
    @Query("select count(a) > 0 from AutorizacionPermiso a where a.residenteId = :residenteId and a.tipo = :tipo and a.revocada = false "
            + "and a.inicio <= :fecha and (a.fin is null or a.fin >= :fecha)")
    boolean vigente(@Param("residenteId") Long residenteId, @Param("tipo") TipoPermiso tipo, @Param("fecha") LocalDate fecha);
}
