package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.entity.AutorizacionPermiso;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AutorizacionPermisoRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/** Autorizaciones previas por estudiante y tipo de permiso. Las registra y revoca la administración; el historial no se borra. */
@Service
@RequiredArgsConstructor
public class AutorizacionPermisoService {

    public record AutorizacionResponse(Long id, Long residenteId, TipoPermiso tipo, LocalDate inicio, LocalDate fin, String referencia,
                                       Instant registradaEn, boolean revocada, String motivoRevocacion, boolean vigente) {
    }

    private final AutorizacionPermisoRepository autorizaciones;
    private final ResidenteRepository residentes;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    /** Si el estudiante puede enviar ese tipo de permiso directo al pastor en esa fecha de salida. */
    @Transactional(readOnly = true)
    public boolean vigente(Long residenteId, TipoPermiso tipo, LocalDate fechaSalida) {
        return autorizaciones.vigente(residenteId, tipo, fechaSalida);
    }

    @Transactional(readOnly = true)
    public List<AutorizacionResponse> deResidente(Long residenteId) {
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        return autorizaciones.findByResidenteIdOrderByIdDesc(residenteId).stream().map(a -> aResponse(a, hoy)).toList();
    }

    @Transactional
    public AutorizacionResponse registrar(Long residenteId, TipoPermiso tipo, LocalDate inicio, LocalDate fin, String referencia) {
        residentes.findById(residenteId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
        if (referencia == null || referencia.isBlank()) {
            throw new Excepciones.SolicitudInvalida("Datos inválidos", Map.of("referencia", "La referencia (número de la hoja firmada) es obligatoria"));
        }
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        LocalDate desde = inicio == null ? hoy : inicio;
        if (fin != null && fin.isBefore(desde)) {
            throw new Excepciones.SolicitudInvalida("Datos inválidos", Map.of("fin", "La fecha de fin no puede ser anterior al inicio"));
        }
        var a = new AutorizacionPermiso();
        a.setResidenteId(residenteId);
        a.setTipo(tipo);
        a.setInicio(desde);
        a.setFin(fin);
        a.setReferencia(referencia.trim());
        a.setRegistradaPor(sesion.obtener().id());
        a.setRegistradaEn(reloj.instant());
        a = autorizaciones.save(a);
        auditoria.registrar(sesion.obtener().id(), "AUTORIZACION_REGISTRADA", "autorizacion_permiso", a.getId(), tipo + " " + a.getReferencia());
        return aResponse(a, hoy);
    }

    @Transactional
    public AutorizacionResponse revocar(Long id, String motivo) {
        var a = autorizaciones.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Autorización no encontrada"));
        if (a.isRevocada()) {
            throw new Excepciones.Conflicto("La autorización ya está revocada");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new Excepciones.SolicitudInvalida("Datos inválidos", Map.of("motivo", "El motivo es obligatorio"));
        }
        a.setRevocada(true);
        a.setRevocadaPor(sesion.obtener().id());
        a.setRevocadaEn(reloj.instant());
        a.setMotivoRevocacion(motivo.trim());
        auditoria.registrar(sesion.obtener().id(), "AUTORIZACION_REVOCADA", "autorizacion_permiso", id, motivo.trim());
        return aResponse(a, reloj.instant().atZone(zona).toLocalDate());
    }

    private AutorizacionResponse aResponse(AutorizacionPermiso a, LocalDate hoy) {
        boolean vigente = !a.isRevocada() && !a.getInicio().isAfter(hoy) && (a.getFin() == null || !a.getFin().isBefore(hoy));
        return new AutorizacionResponse(a.getId(), a.getResidenteId(), a.getTipo(), a.getInicio(), a.getFin(), a.getReferencia(), a.getRegistradaEn(),
                a.isRevocada(), a.getMotivoRevocacion(), vigente);
    }
}
