package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.dto.response.AuditoriaResponse;
import pe.upeu.resi.ResiAPI.entity.RegistroAuditoria;
import pe.upeu.resi.ResiAPI.repository.RegistroAuditoriaRepository;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final RegistroAuditoriaRepository repositorio;
    private final Clock reloj;

    /** Se ejecuta en la transacción de la operación auditada: si ésta falla, el registro también se revierte. */
    public void registrar(Long usuarioId, String accion, String entidad, Long entidadId, String detalle) {
        var r = new RegistroAuditoria();
        r.setUsuarioId(usuarioId);
        r.setAccion(accion);
        r.setEntidad(entidad);
        r.setEntidadId(entidadId);
        r.setDetalle(detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 500));
        r.setCreadoEn(reloj.instant());
        repositorio.save(r);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AuditoriaResponse> listar(Pageable pageable) {
        return PaginaResponse.de(repositorio.findAllByOrderByCreadoEnDesc(pageable),
                r -> new AuditoriaResponse(r.getId(), r.getUsuarioId(), r.getAccion(), r.getEntidad(), r.getEntidadId(), r.getDetalle(), r.getCreadoEn()));
    }
}
