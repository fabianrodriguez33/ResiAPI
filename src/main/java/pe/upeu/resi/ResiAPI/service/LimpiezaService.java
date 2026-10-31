package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.LimpiezaRequest;
import pe.upeu.resi.ResiAPI.dto.response.LimpiezaResponses.EvaluacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.LimpiezaResponses.ResumenLimpiezaResponse;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.entity.DetalleEvaluacionLimpieza;
import pe.upeu.resi.ResiAPI.entity.EvaluacionLimpieza;
import pe.upeu.resi.ResiAPI.enums.CriterioLimpieza;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.DetalleEvaluacionLimpiezaRepository;
import pe.upeu.resi.ResiAPI.repository.EvaluacionLimpiezaRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/** Evaluación de limpieza individual (propuesta de 20 puntos). No aplica sanciones ni umbrales automáticos. */
@Service
@RequiredArgsConstructor
public class LimpiezaService {

    public static final int MAXIMO_TOTAL = java.util.Arrays.stream(CriterioLimpieza.values()).mapToInt(CriterioLimpieza::getMaximo).sum();

    private final EvaluacionLimpiezaRepository evaluaciones;
    private final DetalleEvaluacionLimpiezaRepository detalles;
    private final ResidenteRepository residentes;
    private final ResidenteService residenteService;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    @Transactional
    public EvaluacionResponse registrar(LimpiezaRequest r) {
        var residente = residentes.findById(r.residenteId()).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
        if (!residente.isActivo()) {
            throw new Excepciones.Conflicto("No se puede evaluar a un residente inactivo");
        }
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        if (r.fecha().isAfter(hoy)) {
            throw new Excepciones.SolicitudInvalida("La fecha no puede ser futura", Map.of("fecha", "La fecha no puede ser futura"));
        }
        validarPuntajes(r.puntajes());
        if (evaluaciones.existsByResidenteIdAndFecha(r.residenteId(), r.fecha())) {
            throw new Excepciones.Conflicto("El residente ya tiene una evaluación en esa fecha");
        }
        int total = r.puntajes().values().stream().mapToInt(Integer::intValue).sum();
        var e = new EvaluacionLimpieza();
        e.setResidenteId(r.residenteId());
        e.setFecha(r.fecha());
        e.setEvaluadorId(sesion.obtener().id());
        e.setTotal((short) total);
        e.setObservacion(r.observacion() == null || r.observacion().isBlank() ? null : r.observacion().trim());
        e.setCreadoEn(reloj.instant());
        e = evaluaciones.save(e);
        for (var entrada : r.puntajes().entrySet()) {
            var d = new DetalleEvaluacionLimpieza();
            d.setEvaluacionId(e.getId());
            d.setCriterio(entrada.getKey());
            d.setPuntaje(entrada.getValue().shortValue());
            detalles.save(d);
        }
        auditoria.registrar(e.getEvaluadorId(), "LIMPIEZA_REGISTRADA", "evaluacion_limpieza", e.getId(), "total=" + total);
        return aResponse(e, new EnumMap<>(r.puntajes()));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<EvaluacionResponse> deResidente(Long residenteId, Pageable pageable) {
        residenteService.buscar(residenteId);
        return pagina(residenteId, pageable);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<EvaluacionResponse> mias(Pageable pageable) {
        return pagina(residenteService.residenteActual().getId(), pageable);
    }

    @Transactional(readOnly = true)
    public ResumenLimpiezaResponse resumenMio() {
        Long id = residenteService.residenteActual().getId();
        var todas = evaluaciones.findByResidenteIdOrderByFechaDesc(id, PageRequest.of(0, 1000)).getContent();
        if (todas.isEmpty()) {
            return new ResumenLimpiezaResponse(0, null, null, null);
        }
        double promedio = todas.stream().mapToInt(EvaluacionLimpieza::getTotal).average().orElse(0);
        return new ResumenLimpiezaResponse(todas.size(), Math.round(promedio * 10) / 10.0, (int) todas.get(0).getTotal(), todas.get(0).getFecha());
    }

    private void validarPuntajes(Map<CriterioLimpieza, Integer> puntajes) {
        Map<String, String> errores = new java.util.LinkedHashMap<>();
        for (var criterio : CriterioLimpieza.values()) {
            Integer p = puntajes.get(criterio);
            if (p == null) {
                errores.put(criterio.name(), "Falta el puntaje");
            } else if (p < 0 || p > criterio.getMaximo()) {
                errores.put(criterio.name(), "Debe estar entre 0 y " + criterio.getMaximo());
            }
        }
        if (!errores.isEmpty()) {
            throw new Excepciones.SolicitudInvalida("Puntajes inválidos", errores);
        }
    }

    private PaginaResponse<EvaluacionResponse> pagina(Long residenteId, Pageable pageable) {
        var pagina = evaluaciones.findByResidenteIdOrderByFechaDesc(residenteId, pageable);
        var porEvaluacion = detalles.findByEvaluacionIdIn(pagina.getContent().stream().map(EvaluacionLimpieza::getId).toList()).stream()
                .collect(Collectors.groupingBy(DetalleEvaluacionLimpieza::getEvaluacionId));
        return PaginaResponse.de(pagina, e -> {
            Map<CriterioLimpieza, Integer> mapa = new EnumMap<>(CriterioLimpieza.class);
            porEvaluacion.getOrDefault(e.getId(), java.util.List.of()).forEach(d -> mapa.put(d.getCriterio(), (int) d.getPuntaje()));
            return aResponse(e, mapa);
        });
    }

    private static EvaluacionResponse aResponse(EvaluacionLimpieza e, Map<CriterioLimpieza, Integer> puntajes) {
        return new EvaluacionResponse(e.getId(), e.getResidenteId(), e.getFecha(), e.getTotal(), MAXIMO_TOTAL, puntajes,
                e.getObservacion(), e.getEvaluadorId(), e.getCreadoEn());
    }
}
