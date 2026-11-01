package pe.upeu.resi.ResiAPI.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.resi.ResiAPI.entity.ResidenteApoderado;

public interface ResidenteApoderadoRepository extends JpaRepository<ResidenteApoderado, ResidenteApoderado.Clave> {

    List<ResidenteApoderado> findByResidenteId(Long residenteId);

    List<ResidenteApoderado> findByApoderadoId(Long apoderadoId);
}
