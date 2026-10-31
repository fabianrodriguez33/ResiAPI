package pe.upeu.resi.ResiAPI.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.QrResidente;

public interface QrResidenteRepository extends JpaRepository<QrResidente, Long> {

    Optional<QrResidente> findByResidenteIdAndActivoTrue(Long residenteId);

    Optional<QrResidente> findByCodigo(UUID codigo);
}
