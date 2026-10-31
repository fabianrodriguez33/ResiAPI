package pe.upeu.resi.ResiAPI.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.resi.ResiAPI.entity.SesionCulto;
import pe.upeu.resi.ResiAPI.enums.TipoCulto;

public interface SesionCultoRepository extends JpaRepository<SesionCulto, Long> {

    Optional<SesionCulto> findByFechaAndTipo(LocalDate fecha, TipoCulto tipo);

    List<SesionCulto> findByFechaOrderByInicioAsc(LocalDate fecha);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionCulto s where s.id = :id")
    Optional<SesionCulto> findByIdForUpdate(@Param("id") Long id);
}
