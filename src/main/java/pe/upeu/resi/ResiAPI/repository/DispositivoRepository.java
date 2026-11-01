package pe.upeu.resi.ResiAPI.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.Dispositivo;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByToken(String token);

    List<Dispositivo> findByUsuarioId(Long usuarioId);

    void deleteByToken(String token);
}
