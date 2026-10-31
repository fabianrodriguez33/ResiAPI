package pe.upeu.resi.ResiAPI.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.request.ResidenteDtos.HabitacionRequest;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.HabitacionResponse;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.OcupacionResponse;
import pe.upeu.resi.ResiAPI.entity.AsignacionHabitacion;
import pe.upeu.resi.ResiAPI.entity.Habitacion;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsignacionHabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.HabitacionRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

@Service
@RequiredArgsConstructor
public class HabitacionService {

    private final HabitacionRepository habitaciones;
    private final AsignacionHabitacionRepository asignaciones;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;

    @Transactional(readOnly = true)
    public List<HabitacionResponse> listar() {
        Map<Long, Long> ocupantes = ocupantesPorHabitacion();
        return habitaciones.findAllByOrderByNumeroAsc().stream()
                .map(h -> aResponse(h, ocupantes.getOrDefault(h.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public OcupacionResponse ocupacion() {
        Map<Long, Long> ocupantes = ocupantesPorHabitacion();
        var activas = habitaciones.findAllByOrderByNumeroAsc().stream().filter(Habitacion::isActiva).toList();
        long capacidad = activas.stream().mapToLong(Habitacion::getCapacidad).sum();
        long ocupados = ocupantes.values().stream().mapToLong(Long::longValue).sum();
        return new OcupacionResponse(activas.size(), capacidad, ocupados, Math.max(capacidad - ocupados, 0));
    }

    @Transactional
    public HabitacionResponse crear(HabitacionRequest r) {
        String numero = r.numero().trim();
        if (habitaciones.existsByNumero(numero)) {
            throw new Excepciones.Conflicto("Ya existe la habitación " + numero);
        }
        var h = new Habitacion();
        h.setNumero(numero);
        h.setCapacidad(r.capacidad().shortValue());
        h.setActiva(r.activa() == null || r.activa());
        h = habitaciones.save(h);
        auditoria.registrar(sesion.obtener().id(), "HABITACION_CREADA", "habitacion", h.getId(), "capacidad=" + h.getCapacidad());
        return aResponse(h, 0);
    }

    @Transactional
    public HabitacionResponse actualizar(Long id, HabitacionRequest r) {
        var h = habitaciones.findByIdForUpdate(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Habitación no encontrada"));
        long ocupantes = asignaciones.countByHabitacionIdAndHastaIsNull(id);
        if (r.capacidad() < ocupantes) {
            throw new Excepciones.Conflicto("La capacidad no puede ser menor a los " + ocupantes + " residentes asignados");
        }
        boolean activa = r.activa() == null || r.activa();
        if (!activa && ocupantes > 0) {
            throw new Excepciones.Conflicto("No se puede desactivar una habitación con residentes asignados");
        }
        String numero = r.numero().trim();
        if (!numero.equals(h.getNumero()) && habitaciones.existsByNumero(numero)) {
            throw new Excepciones.Conflicto("Ya existe la habitación " + numero);
        }
        h.setNumero(numero);
        h.setCapacidad(r.capacidad().shortValue());
        h.setActiva(activa);
        auditoria.registrar(sesion.obtener().id(), "HABITACION_ACTUALIZADA", "habitacion", id, "capacidad=" + h.getCapacidad());
        return aResponse(h, ocupantes);
    }

    private Map<Long, Long> ocupantesPorHabitacion() {
        return asignaciones.findByHastaIsNull().stream()
                .collect(Collectors.groupingBy(AsignacionHabitacion::getHabitacionId, Collectors.counting()));
    }

    private static HabitacionResponse aResponse(Habitacion h, long ocupantes) {
        return new HabitacionResponse(h.getId(), h.getNumero(), h.getCapacidad(), h.isActiva(), ocupantes, Math.max(h.getCapacidad() - ocupantes, 0));
    }
}
