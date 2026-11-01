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
import pe.upeu.resi.ResiAPI.entity.Residente;

public interface ResidenteRepository extends JpaRepository<Residente, Long> {

    java.util.List<Residente> findByActivoTrueOrderByCodigoAsc();

    Optional<Residente> findByUsuarioId(Long usuarioId);

    boolean existsByCodigo(String codigo);

    Optional<Residente> findByCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Residente r where r.id = :id")
    Optional<Residente> findByIdForUpdate(@Param("id") Long id);

    List<Residente> findByActivoTrue();

    Page<Residente> findAllByOrderByCodigoAsc(Pageable pageable);
}
