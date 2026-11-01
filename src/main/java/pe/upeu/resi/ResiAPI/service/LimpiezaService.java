package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.LimpiezaHabitacionRequest;
import pe.upeu.resi.ResiAPI.dto.request.LimpiezaRequest;
import pe.upeu.resi.ResiAPI.dto.response.LimpiezaResponses.HabitacionLimpiezaResponse;
import pe.upeu.resi.ResiAPI.dto.response.LimpiezaResponses.ResidenteLimpieza;
import pe.upeu.resi.ResiAPI.entity.AsignacionHabitacion;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.entity.Usuario;
import pe.upeu.resi.ResiAPI.repository.AsignacionHabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.HabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
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

/** Evaluación de limpieza (propuesta de 20 puntos): personal por residente; basura y olores comunes a la habitación. No aplica sanciones ni umbrales automáticos. */
@Service
@RequiredArgsConstructor
public class LimpiezaService {

    public static final int MAXIMO_TOTAL = java.util.Arrays.stream(CriterioLimpieza.values()).mapToInt(CriterioLimpieza::getMaximo).sum();

    private final EvaluacionLimpiezaRepository evaluaciones;
    private final DetalleEvaluacionLimpiezaRepository detalles;
    private final ResidenteRepository residentes;
    private final ResidenteService residenteService;
    private final HabitacionRepository habitacionesRepo;
    private final AsignacionHabitacionRepository asignaciones;
    private final UsuarioRepository usuarios;
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
        if (r.fecha().getDayOfWeek() == java.time.DayOfWeek.SATURDAY || r.fecha().getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            throw new Excepciones.SolicitudInvalida("La limpieza se evalúa de lunes a viernes", Map.of("fecha", "Elige un día de lunes a viernes"));
        }
        validarPuntajes(r.puntajes());
        if (evaluaciones.existsByResidenteIdAndFecha(r.residenteId(), r.fecha())) {
            throw new Excepciones.Conflicto("El residente ya tiene una evaluación en esa fecha");
        }
        return guardar(r.residenteId(), r.fecha(), null, r.puntajes(), r.observacion());
    }

    private EvaluacionResponse guardar(Long residenteId, LocalDate fecha, Long habitacionId, Map<CriterioLimpieza, Integer> puntajes, String observacion) {
        int total = puntajes.values().stream().mapToInt(Integer::intValue).sum();
        var e = new EvaluacionLimpieza();
        e.setResidenteId(residenteId);
        e.setHabitacionId(habitacionId);
        e.setFecha(fecha);
        e.setEvaluadorId(sesion.obtener().id());
        e.setTotal((short) total);
        e.setObservacion(observacion == null || observacion.isBlank() ? null : observacion.trim());
        e.setCreadoEn(reloj.instant());
        e = evaluaciones.save(e);
        for (var entrada : puntajes.entrySet()) {
            var d = new DetalleEvaluacionLimpieza();
            d.setEvaluacionId(e.getId());
            d.setCriterio(entrada.getKey());
            d.setPuntaje(entrada.getValue().shortValue());
            detalles.save(d);
        }
        auditoria.registrar(e.getEvaluadorId(), "LIMPIEZA_REGISTRADA", "evaluacion_limpieza", e.getId(), "total=" + total);
        return aResponse(e, new EnumMap<>(puntajes));
    }

    /** Habitaciones con ocupantes y el estado de evaluación de cada uno en [fecha] (hoy por omisión). */
    @Transactional(readOnly = true)
    public List<HabitacionLimpiezaResponse> habitaciones(LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : reloj.instant().atZone(zona).toLocalDate();
        var vigentes = asignaciones.findByHastaIsNull();
        var residentesPorId = residentes.findAllById(vigentes.stream().map(AsignacionHabitacion::getResidenteId).toList()).stream()
                .filter(Residente::isActivo).collect(Collectors.toMap(Residente::getId, Function.identity()));
        var nombres = usuarios.findAllById(residentesPorId.values().stream().map(Residente::getUsuarioId).toList()).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
        var evaluados = evaluaciones.findByResidenteIdInAndFecha(residentesPorId.keySet(), dia).stream()
                .map(EvaluacionLimpieza::getResidenteId).collect(Collectors.toSet());
        var porHabitacion = vigentes.stream().filter(a -> residentesPorId.containsKey(a.getResidenteId()))
                .collect(Collectors.groupingBy(AsignacionHabitacion::getHabitacionId));
        return habitacionesRepo.findAllByOrderByNumeroAsc().stream()
                .filter(h -> h.isActiva() && porHabitacion.containsKey(h.getId()))
                .map(h -> {
                    var lista = porHabitacion.get(h.getId()).stream()
                            .sorted(Comparator.comparing(a -> a.getCama() == null ? "~" : a.getCama()))
                            .map(a -> {
                                var r = residentesPorId.get(a.getResidenteId());
                                return new ResidenteLimpieza(r.getId(), r.getCodigo(), nombres.get(r.getUsuarioId()), a.getCama(), evaluados.contains(r.getId()));
                            }).toList();
                    return new HabitacionLimpiezaResponse(h.getId(), h.getNumero(), h.getCapacidad(), lista, lista.stream().filter(ResidenteLimpieza::evaluado).count());
                }).toList();
    }

    /** Basura y olores son comunes a la habitación; el resto es personal. Todo o nada. */
    @Transactional
    public List<EvaluacionResponse> registrarHabitacion(Long habitacionId, LimpiezaHabitacionRequest r) {
        var hab = habitacionesRepo.findByIdForUpdate(habitacionId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Habitación no encontrada"));
        if (!hab.isActiva()) {
            throw new Excepciones.Conflicto("La habitación " + hab.getNumero() + " está inactiva");
        }
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        if (r.fecha().isAfter(hoy)) {
            throw new Excepciones.SolicitudInvalida("La fecha no puede ser futura", Map.of("fecha", "La fecha no puede ser futura"));
        }
        if (r.fecha().getDayOfWeek() == java.time.DayOfWeek.SATURDAY || r.fecha().getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            throw new Excepciones.SolicitudInvalida("La limpieza se evalúa de lunes a viernes", Map.of("fecha", "Elige un día de lunes a viernes"));
        }
        var ocupantes = asignaciones.findByHabitacionIdAndHastaIsNull(habitacionId).stream().map(AsignacionHabitacion::getResidenteId).collect(Collectors.toSet());
        var vistos = new java.util.HashSet<Long>();
        Map<String, String> errores = new java.util.LinkedHashMap<>();
        validarCriterio(errores, "basura", CriterioLimpieza.BASURA, r.basura());
        validarCriterio(errores, "olores", CriterioLimpieza.OLORES, r.olores());
        for (int i = 0; i < r.residentes().size(); i++) {
            var p = r.residentes().get(i);
            String pre = "residentes[" + i + "].";
            validarCriterio(errores, pre + "ropero", CriterioLimpieza.ROPERO, p.ropero());
            validarCriterio(errores, pre + "cama", CriterioLimpieza.CAMA, p.cama());
            validarCriterio(errores, pre + "escritorio", CriterioLimpieza.ESCRITORIO, p.escritorio());
            validarCriterio(errores, pre + "piso", CriterioLimpieza.PISO, p.piso());
            if (!vistos.add(p.residenteId())) {
                errores.put(pre + "residenteId", "Residente repetido");
            } else if (!ocupantes.contains(p.residenteId())) {
                errores.put(pre + "residenteId", "El residente no está asignado a la habitación " + hab.getNumero());
            }
        }
        if (!errores.isEmpty()) {
            throw new Excepciones.SolicitudInvalida("Evaluación inválida", errores);
        }
        var activos = residentes.findAllById(vistos).stream().filter(Residente::isActivo).map(Residente::getId).collect(Collectors.toSet());
        if (activos.size() != vistos.size()) {
            throw new Excepciones.Conflicto("No se puede evaluar a un residente inactivo");
        }
        if (!evaluaciones.findByResidenteIdInAndFecha(vistos, r.fecha()).isEmpty()) {
            throw new Excepciones.Conflicto("Alguno de los residentes ya tiene una evaluación en esa fecha");
        }
        // Si la habitación ya fue evaluada ese día (otros residentes), basura y olores deben coincidir.
        var previas = evaluaciones.findByHabitacionIdAndFecha(habitacionId, r.fecha());
        if (!previas.isEmpty()) {
            var comunes = detalles.findByEvaluacionIdIn(List.of(previas.get(0).getId())).stream()
                    .filter(d -> d.getCriterio() == CriterioLimpieza.BASURA || d.getCriterio() == CriterioLimpieza.OLORES)
                    .collect(Collectors.toMap(DetalleEvaluacionLimpieza::getCriterio, d -> (int) d.getPuntaje()));
            if (!Integer.valueOf(r.basura()).equals(comunes.get(CriterioLimpieza.BASURA)) || !Integer.valueOf(r.olores()).equals(comunes.get(CriterioLimpieza.OLORES))) {
                throw new Excepciones.Conflicto("Basura y olores de la habitación " + hab.getNumero() + " ya se registraron ese día con otros valores ("
                        + comunes.get(CriterioLimpieza.BASURA) + " y " + comunes.get(CriterioLimpieza.OLORES) + ")");
            }
        }
        return r.residentes().stream().map(p -> {
            Map<CriterioLimpieza, Integer> puntajes = new EnumMap<>(CriterioLimpieza.class);
            puntajes.put(CriterioLimpieza.ROPERO, p.ropero());
            puntajes.put(CriterioLimpieza.CAMA, p.cama());
            puntajes.put(CriterioLimpieza.ESCRITORIO, p.escritorio());
            puntajes.put(CriterioLimpieza.BASURA, r.basura());
            puntajes.put(CriterioLimpieza.OLORES, r.olores());
            puntajes.put(CriterioLimpieza.PISO, p.piso());
            return guardar(p.residenteId(), r.fecha(), habitacionId, puntajes, p.observacion());
        }).toList();
    }

    private static void validarCriterio(Map<String, String> errores, String campo, CriterioLimpieza criterio, Integer valor) {
        if (valor == null || valor < 0 || valor > criterio.getMaximo()) {
            errores.put(campo, "Debe estar entre 0 y " + criterio.getMaximo());
        }
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
