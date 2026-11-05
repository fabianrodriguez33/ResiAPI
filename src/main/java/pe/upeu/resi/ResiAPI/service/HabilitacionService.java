package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.entity.JustificacionFalta;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsistenciaCultoRepository;
import pe.upeu.resi.ResiAPI.repository.DiaSinCultoRepository;
import pe.upeu.resi.ResiAPI.repository.EvaluacionLimpiezaRepository;
import pe.upeu.resi.ResiAPI.repository.JustificacionFaltaRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.SesionCultoRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/**
 * Habilitación semanal para solicitar permisos. La semana va de domingo a sábado y se evalúa de lunes a viernes:
 * - Falta = día de lunes a viernes con culto realizado (hubo sesión y no es un día sin culto) en el que el residente no asistió a
 *   ninguno de los cultos. No cuenta si el pastor la justificó (con motivo, hasta el lunes de la semana siguiente).
 * - Está habilitado esta semana quien en la semana anterior NO acumuló dos faltas sin justificar y, si tuvo evaluaciones de limpieza,
 *   alcanzó un promedio de al menos 13/20. Sin evaluaciones de limpieza solo cuentan los cultos.
 * Se calcula al momento con los datos de la semana anterior, así que al justificarse una falta o al cumplirse los requisitos la
 * habilitación vuelve sola desde el domingo siguiente.
 */
@Service
@RequiredArgsConstructor
public class HabilitacionService {

    public static final int FALTAS_PARA_SUSPENDER = 2;
    public static final double LIMPIEZA_MINIMA = 13.0;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    public record Falta(LocalDate fecha, boolean justificada, String motivo) {
    }

    public record Estado(boolean habilitado, LocalDate desde, LocalDate hasta, String motivo, int faltasNoJustificadas,
                         long evaluacionesLimpieza, Double promedioLimpieza, double limpiezaMinima, List<Falta> faltas) {
    }

    public record FaltaPendiente(Long residenteId, String codigo, String nombre, LocalDate fecha, LocalDate plazo) {
    }

    private final AsistenciaCultoRepository asistencias;
    private final SesionCultoRepository sesiones;
    private final DiaSinCultoRepository diasSinCulto;
    private final JustificacionFaltaRepository justificaciones;
    private final EvaluacionLimpiezaRepository evaluaciones;
    private final ResidenteRepository residentes;
    private final UsuarioRepository usuarios;
    private final ResidenteService residenteService;
    private final NotificacionService avisos;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    public LocalDate hoy() {
        return reloj.instant().atZone(zona).toLocalDate();
    }

    static LocalDate domingoDe(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    /** Fecha límite para justificar una falta: el lunes de la semana siguiente a la de la falta. */
    static LocalDate plazoParaJustificar(LocalDate falta) {
        return domingoDe(falta).plusDays(8);
    }

    // ------------------------------------------------------------------ estado

    @Transactional(readOnly = true)
    public Estado deMiResidente() {
        return estado(residenteService.residenteActual(), hoy());
    }

    @Transactional(readOnly = true)
    public Estado estado(Residente r, LocalDate hoy) {
        LocalDate domingo = domingoDe(hoy);
        LocalDate lunesAnterior = domingo.minusDays(6), viernesAnterior = domingo.minusDays(2);
        List<Falta> faltas = faltasEntre(r, lunesAnterior, viernesAnterior);
        int noJustificadas = (int) faltas.stream().filter(f -> !f.justificada()).count();
        Object[] fila = evaluaciones.estadisticas(r.getId(), lunesAnterior, viernesAnterior).get(0);
        long n = ((Number) fila[0]).longValue();
        Double promedio = fila[1] == null ? null : ((Number) fila[1]).doubleValue();
        String motivo = null;
        if (noJustificadas >= FALTAS_PARA_SUSPENDER) {
            motivo = "faltaste " + noJustificadas + " días a los cultos la semana pasada sin justificación";
        } else if (n > 0 && promedio != null && promedio < LIMPIEZA_MINIMA) {
            motivo = "tu promedio de limpieza de la semana pasada fue " + String.format("%.1f", promedio) + "/20 (mínimo 13/20)";
        }
        return new Estado(motivo == null, domingo, domingo.plusDays(6), motivo, noJustificadas, n, promedio, LIMPIEZA_MINIMA, faltas);
    }

    /** Días de lunes a viernes de [desde, hasta] con culto realizado en los que el residente no asistió, marcando los justificados. */
    List<Falta> faltasEntre(Residente r, LocalDate desde, LocalDate hasta) {
        Set<LocalDate> conCulto = new HashSet<>(sesiones.fechasConSesion(desde, hasta));
        diasSinCulto.findByFechaBetweenOrderByFechaAsc(desde, hasta).forEach(d -> conCulto.remove(d.getFecha()));
        Set<LocalDate> asistidas = new HashSet<>(asistencias.fechasAsistidas(r.getId(), desde, hasta));
        Map<LocalDate, String> justificadas = new HashMap<>();
        justificaciones.findByResidenteIdAndFechaBetween(r.getId(), desde, hasta).forEach(j -> justificadas.put(j.getFecha(), j.getMotivo()));
        LocalDate alta = r.getCreadoEn() == null ? desde : r.getCreadoEn().atZone(zona).toLocalDate();
        List<Falta> resultado = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            boolean laborable = d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
            if (!laborable || !conCulto.contains(d) || d.isBefore(alta) || asistidas.contains(d)) {
                continue;
            }
            resultado.add(new Falta(d, justificadas.containsKey(d), justificadas.get(d)));
        }
        return resultado;
    }

    // ------------------------------------------------------------------ bloqueo

    /** Impide crear solicitudes mientras el residente está inhabilitado. [propio] distingue su mensaje del que ve un padre. */
    @Transactional(readOnly = true)
    public void exigirHabilitado(Residente r, boolean propio) {
        var e = estado(r, hoy());
        if (!e.habilitado()) {
            String hasta = DIA.format(e.hasta());
            throw new Excepciones.Conflicto((propio ? "Estás inhabilitado" : "El residente está inhabilitado") + " para solicitar permisos hasta el sábado "
                    + hasta + ": " + e.motivo() + ". Si es una falta, el pastor puede justificarla hasta el lunes.");
        }
    }

    /** Verdadero si el residente está inhabilitado esta semana y la salida cae dentro de esta semana (domingo a sábado). */
    @Transactional(readOnly = true)
    public boolean suspendidoParaSalida(Residente r, java.time.Instant salida) {
        LocalDate hoy = hoy();
        LocalDate dia = salida.atZone(zona).toLocalDate();
        LocalDate domingo = domingoDe(hoy);
        return !dia.isBefore(domingo) && !dia.isAfter(domingo.plusDays(6)) && !estado(r, hoy).habilitado();
    }

    // ------------------------------------------------------------------ justificación (pastor)

    @Transactional(readOnly = true)
    public List<FaltaPendiente> faltasPorJustificar() {
        LocalDate hoy = hoy();
        LocalDate domingo = domingoDe(hoy);
        // Esta semana (los días ya pasados) y, hasta el lunes, la semana anterior.
        LocalDate desde = hoy.isAfter(domingo.plusDays(1)) ? domingo : domingo.minusDays(7);
        LocalDate hasta = hoy.minusDays(1);
        List<FaltaPendiente> lista = new ArrayList<>();
        if (hasta.isBefore(desde)) {
            return lista;
        }
        for (Residente r : residentes.findByActivoTrueOrderByCodigoAsc()) {
            String nombre = usuarios.findById(r.getUsuarioId()).map(u -> u.getNombreCompleto()).orElse(r.getCodigo());
            for (Falta f : faltasEntre(r, desde, hasta)) {
                if (!f.justificada() && !hoy.isAfter(plazoParaJustificar(f.fecha()))) {
                    lista.add(new FaltaPendiente(r.getId(), r.getCodigo(), nombre, f.fecha(), plazoParaJustificar(f.fecha())));
                }
            }
        }
        return lista;
    }

    @Transactional
    public void justificar(Long residenteId, LocalDate fecha, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new Excepciones.SolicitudInvalida("Escribe el motivo de la justificación", Map.of("motivo", "El motivo es obligatorio"));
        }
        var r = residentes.findById(residenteId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
        LocalDate hoy = hoy();
        if (!fecha.isBefore(hoy)) {
            throw new Excepciones.Conflicto("Solo se justifican faltas de días que ya pasaron");
        }
        if (justificaciones.existsByResidenteIdAndFecha(residenteId, fecha)) {
            throw new Excepciones.Conflicto("Esa falta ya está justificada");
        }
        if (faltasEntre(r, fecha, fecha).isEmpty()) {
            throw new Excepciones.Conflicto("Ese día no cuenta como falta de este residente");
        }
        LocalDate plazo = plazoParaJustificar(fecha);
        if (hoy.isAfter(plazo)) {
            throw new Excepciones.Conflicto("El plazo para justificar venció el lunes " + DIA.format(plazo));
        }
        var j = new JustificacionFalta();
        j.setResidenteId(residenteId);
        j.setFecha(fecha);
        j.setMotivo(motivo.trim());
        j.setRegistradaPor(sesion.obtener().id());
        j.setRegistradaEn(reloj.instant());
        justificaciones.save(j);
        auditoria.registrar(j.getRegistradaPor(), "FALTA_JUSTIFICADA", "residente", residenteId, fecha + ": " + j.getMotivo());
        avisos.alResidente(residenteId, "Falta justificada", "El pastor justificó tu falta del " + DIA.format(fecha) + ". Motivo: " + j.getMotivo());
    }
}
