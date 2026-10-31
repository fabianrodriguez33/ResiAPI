package pe.upeu.resi.ResiAPI.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.AprobacionApoderado;

public interface AprobacionApoderadoRepository extends JpaRepository<AprobacionApoderado, Long> {

    Optional<AprobacionApoderado> findByTokenHash(String tokenHash);

    List<AprobacionApoderado> findBySolicitudIdAndUsadoEnIsNull(Long solicitudId);
}
