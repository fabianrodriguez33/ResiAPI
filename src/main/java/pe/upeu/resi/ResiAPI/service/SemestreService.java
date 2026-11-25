package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.entity.Semestre;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsignacionHabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.SemestreRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/**
 * Semestres de la residencia. Crear uno nuevo cierra el activo y deja todas las habitaciones vacías (las asignaciones vigentes se cierran, no se borran):
 * los residentes conservan su cuenta y el historial, y la administración los vuelve a asignar.
 */
@Service
@RequiredArgsConstructor
public class SemestreService {

    public record SemestreResponse(Long id, String nombre, LocalDate inicio, LocalDate fin, boolean activo) {
    }

    private final SemestreRepository semestres;
    private final AsignacionHabitacionRepository asignaciones;
    private final ConfiguracionService configuracion;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public List<SemestreResponse> listar() {
        return semestres.findAllByOrderByInicioDesc().stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public SemestreResponse actual() {
        return semestres.findByActivoTrue().map(this::aResponse).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("No hay un semestre activo"));
    }

    @Transactional
    public SemestreResponse crear(String nombre, LocalDate inicio, LocalDate fin) {
        var errores = new java.util.LinkedHashMap<String, String>();
        if (nombre == null || !nombre.trim().matches("[0-9]{4}-[0-9A-Za-z]{1,2}")) {
            errores.put("nombre", "Usa el formato 2027-1");
        } else if (semestres.existsByNombreIgnoreCase(nombre.trim())) {
            errores.put("nombre", "Ya existe el semestre " + nombre.trim());
        }
        if (inicio == null) {
            errores.put("inicio", "Indica la fecha de inicio");
        }
        if (fin == null || (inicio != null && !fin.isAfter(inicio))) {
            errores.put("fin", "La fecha de fin debe ser posterior al inicio");
        }
        if (!errores.isEmpty()) {
            throw new Excepciones.SolicitudInvalida("Datos inválidos", errores);
        }
        var ahora = reloj.instant();
        semestres.findByActivoTrue().ifPresent(s -> {
            s.setActivo(false);
            s.setCerradoEn(ahora);
            semestres.saveAndFlush(s);
        });
        var vigentes = asignaciones.findByHastaIsNull();
        vigentes.forEach(a -> a.setHasta(ahora));
        asignaciones.saveAll(vigentes);
        var nuevo = new Semestre();
        nuevo.setNombre(nombre.trim());
        nuevo.setInicio(inicio);
        nuevo.setFin(fin);
        nuevo.setActivo(true);
        nuevo.setCreadoPor(sesion.obtener().id());
        nuevo.setCreadoEn(ahora);
        nuevo = semestres.save(nuevo);
        // Los rankings y notas del semestre usan estas fechas.
        for (var par : java.util.List.of(java.util.Map.entry("ciclo.inicio", inicio.toString()), java.util.Map.entry("ciclo.fin", fin.toString()))) {
            if (configuracion.texto(par.getKey()).isPresent()) {
                configuracion.actualizar(par.getKey(), par.getValue(), sesion.obtener().id());
            }
        }
        auditoria.registrar(sesion.obtener().id(), "SEMESTRE_CREADO", "semestre", nuevo.getId(), nuevo.getNombre() + " habitaciones_liberadas=" + vigentes.size());
        return aResponse(nuevo);
    }

    /** El administrador termina el semestre actual cualquier día: queda cerrado con fin = hoy. Las habitaciones no se vacían hasta crear el siguiente. */
    @Transactional
    public SemestreResponse terminarActual() {
        var s = semestres.findByActivoTrue().orElseThrow(() -> new Excepciones.Conflicto("No hay un semestre activo"));
        var ahora = reloj.instant();
        LocalDate hoy = ahora.atZone(java.time.ZoneId.of("America/Lima")).toLocalDate();
        s.setActivo(false);
        s.setCerradoEn(ahora);
        if (s.getFin().isAfter(hoy)) {
            s.setFin(hoy.isBefore(s.getInicio()) ? s.getInicio() : hoy);
        }
        auditoria.registrar(sesion.obtener().id(), "SEMESTRE_TERMINADO", "semestre", s.getId(), s.getNombre());
        return aResponse(s);
    }

    private SemestreResponse aResponse(Semestre s) {
        return new SemestreResponse(s.getId(), s.getNombre(), s.getInicio(), s.getFin(), s.isActivo());
    }
}
