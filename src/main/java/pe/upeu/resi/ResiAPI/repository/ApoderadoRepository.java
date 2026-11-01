package pe.upeu.resi.ResiAPI.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.Apoderado;

public interface ApoderadoRepository extends JpaRepository<Apoderado, Long> {

    java.util.Optional<Apoderado> findByUsuarioId(Long usuarioId);


}
