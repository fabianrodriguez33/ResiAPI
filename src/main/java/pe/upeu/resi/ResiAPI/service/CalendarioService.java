package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.entity.DiaSinCulto;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.DiaSinCultoRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/** Días de lunes a viernes en los que no hay cultos. No cortan la racha de asistencia de los residentes. */
@Service
@RequiredArgsConstructor
public class CalendarioService {

    public record DiaSinCultoResponse(LocalDate fecha, String motivo) {
    }

    private static final int MAX_DIAS_CONSULTA = 800;

    private final DiaSinCultoRepository dias;
    private final AuditoriaService auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ZoneId zona;

    /** Sin fechas, devuelve desde un año atrás hasta un año adelante. */
    @Transactional(readOnly = true)
    public List<DiaSinCultoResponse> listar(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
        LocalDate d = desde != null ? desde : hoy.minusYears(1);
        LocalDate h = hasta != null ? hasta : hoy.plusYears(1);
        if (h.isBefore(d) || d.plusDays(MAX_DIAS_CONSULTA).isBefore(h)) {
            throw new Excepciones.SolicitudInvalida("El rango de fechas no es válido");
        }
        return dias.findByFechaBetweenOrderByFechaAsc(d, h).stream().map(x -> new DiaSinCultoResponse(x.getFecha(), x.getMotivo())).toList();
    }

    @Transactional
    public DiaSinCultoResponse marcar(LocalDate fecha, String motivo) {
        if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new Excepciones.SolicitudInvalida("Los cultos son de lunes a viernes", java.util.Map.of("fecha", "Elige un día de lunes a viernes"));
        }
        if (dias.existsById(fecha)) {
            throw new Excepciones.Conflicto("Ese día ya está marcado sin cultos");
        }
        var d = new DiaSinCulto();
        d.setFecha(fecha);
        d.setMotivo(motivo.trim());
        d.setCreadoPor(sesion.obtener().id());
        d.setCreadoEn(reloj.instant());
        dias.save(d);
        auditoria.registrar(d.getCreadoPor(), "DIA_SIN_CULTO_MARCADO", "dia_sin_culto", null, fecha + ": " + d.getMotivo());
        return new DiaSinCultoResponse(fecha, d.getMotivo());
    }

    @Transactional
    public void quitar(LocalDate fecha) {
        var d = dias.findById(fecha).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Ese día no está marcado sin cultos"));
        dias.delete(d);
        auditoria.registrar(sesion.obtener().id(), "DIA_SIN_CULTO_QUITADO", "dia_sin_culto", null, fecha.toString());
    }

    /** Motivo por el que no hay cultos en la fecha, o null si es un día normal. */
    @Transactional(readOnly = true)
    public String motivoSinCulto(LocalDate fecha) {
        return dias.findById(fecha).map(DiaSinCulto::getMotivo).orElse(null);
    }
}
