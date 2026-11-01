package pe.upeu.resi.ResiAPI.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.AsistenciaCulto;

public interface AsistenciaCultoRepository extends JpaRepository<AsistenciaCulto, Long> {

    List<AsistenciaCulto> findBySesionIdAndAnuladaFalse(Long sesionId);

    @org.springframework.data.jpa.repository.Query("select count(a) from AsistenciaCulto a, SesionCulto s where s.id = a.sesionId and a.residenteId = :residenteId and a.anulada = false and s.fecha between :desde and :hasta")
    long contarEnPeriodo(@org.springframework.data.repository.query.Param("residenteId") Long residenteId, @org.springframework.data.repository.query.Param("desde") java.time.LocalDate desde, @org.springframework.data.repository.query.Param("hasta") java.time.LocalDate hasta);

    @org.springframework.data.jpa.repository.Query("select count(a) from AsistenciaCulto a, SesionCulto s where s.id = a.sesionId and a.residenteId = :residenteId and a.anulada = false and a.despuesDelCorte = true and s.fecha between :desde and :hasta")
    long contarTardanzasEnPeriodo(@org.springframework.data.repository.query.Param("residenteId") Long residenteId, @org.springframework.data.repository.query.Param("desde") java.time.LocalDate desde, @org.springframework.data.repository.query.Param("hasta") java.time.LocalDate hasta);

    Optional<AsistenciaCulto> findBySesionIdAndResidenteId(Long sesionId, Long residenteId);

    Page<AsistenciaCulto> findByResidenteIdAndAnuladaFalseOrderByRegistradoEnDesc(Long residenteId, Pageable pageable);
}
