package pe.upeu.resi.ResiAPI.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.response.ReporteResponses.FilaReporteResponse;
import pe.upeu.resi.ResiAPI.dto.response.ReporteResponses.ResumenMensualResponse;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.AsignacionHabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.AsistenciaCultoRepository;
import pe.upeu.resi.ResiAPI.repository.EvaluacionLimpiezaRepository;
import pe.upeu.resi.ResiAPI.repository.HabitacionRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;

/**
 * Notas mensuales.
 * <p>Cultos desde noviembre de 2026: porcentaje exacto (sin redondear) de días asistidos entre días con culto programado, de lunes a
 * viernes, sin contar los días justificados por el pastor: AD 90 % o más, A 68 % o más, B 50 % o más, C menos de 50 %. Sin días con
 * culto no hay nota. Los meses anteriores conservan la escala antigua por cantidad de cultos (AD 20 o más, A 15-19, B 11-14, C 0-10).
 * <p>Limpieza (promedio del mes sobre 20, redondeado): AD 18-20, A 15-17, B 11-14, C 0-10.
 */
@Service
@RequiredArgsConstructor
public class NotasService {

    private final ResidenteRepository residentes;
    private final UsuarioRepository usuarios;
    private final AsistenciaCultoRepository asistencias;
    private final EvaluacionLimpiezaRepository evaluaciones;
    private final AsignacionHabitacionRepository asignaciones;
    private final HabitacionRepository habitaciones;
    private final HabilitacionService habilitacion;
    private final Clock reloj;
    private final ZoneId zona;

    /** Primer mes con la nota de cultos por días. */
    public static final YearMonth NUEVA_METODOLOGIA_DESDE = YearMonth.of(2026, 11);

    public static boolean usaDias(YearMonth ym) {
        return !ym.isBefore(NUEVA_METODOLOGIA_DESDE);
    }

    /** Nota por días con porcentaje exacto (aritmética entera, sin redondear): 89,9 % es A. {@code base} debe ser mayor que cero. */
    public static String notaPorDias(long asistidos, long base) {
        return asistidos * 100 >= 90 * base ? "AD" : asistidos * 100 >= 68 * base ? "A" : asistidos * 100 >= 50 * base ? "B" : "C";
    }

    /** Escala antigua (meses anteriores a noviembre de 2026). */
    public static String notaCultos(long cultos) {
        return cultos >= 20 ? "AD" : cultos >= 15 ? "A" : cultos >= 11 ? "B" : "C";
    }

    public static String notaLimpieza(double promedio) {
        long p = Math.round(promedio);
        return p >= 18 ? "AD" : p >= 15 ? "A" : p >= 11 ? "B" : "C";
    }

    public YearMonth mes(Integer anio, Integer mes) {
        if (anio == null && mes == null) {
            return YearMonth.from(reloj.instant().atZone(zona));
        }
        if (anio == null || mes == null || anio < 2000 || anio > 2100 || mes < 1 || mes > 12) {
            throw new Excepciones.SolicitudInvalida("Mes inválido");
        }
        return YearMonth.of(anio, mes);
    }

    @Transactional(readOnly = true)
    public ResumenMensualResponse resumen(Long residenteId, YearMonth ym) {
        return resumen(residenteId, ym, null);
    }

    private ResumenMensualResponse resumen(Long residenteId, YearMonth ym, java.util.Set<java.time.LocalDate> conCulto) {
        var desde = ym.atDay(1);
        var hasta = ym.atEndOfMonth();
        long cultos = asistencias.contarEnPeriodo(residenteId, desde, hasta);
        long tardanzas = asistencias.contarTardanzasEnPeriodo(residenteId, desde, hasta);
        Object[] fila = evaluaciones.estadisticas(residenteId, desde, hasta).get(0);
        long n = ((Number) fila[0]).longValue();
        Double promedio = fila[1] == null ? null : ((Number) fila[1]).doubleValue();
        String notaLimpieza = promedio == null ? null : notaLimpieza(promedio);
        if (!usaDias(ym)) {
            return new ResumenMensualResponse(ym.getYear(), ym.getMonthValue(), cultos, notaCultos(cultos), tardanzas, n,
                    promedio, notaLimpieza, "ANTERIOR", null, null, null, null, false, false);
        }
        var residente = residentes.findById(residenteId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
        var dias = conCulto == null ? habilitacion.diasDeCultos(residente, desde, hasta) : habilitacion.diasDeCultos(residente, desde, hasta, conCulto);
        boolean sinCultos = dias.base() <= 0;
        Double porcentaje = sinCultos ? null : dias.asistidos() * 100.0 / dias.base();
        boolean provisional = !reloj.instant().atZone(zona).toLocalDate().isAfter(hasta);
        return new ResumenMensualResponse(ym.getYear(), ym.getMonthValue(), cultos, sinCultos ? null : notaPorDias(dias.asistidos(), dias.base()),
                tardanzas, n, promedio, notaLimpieza, "POR_DIAS", dias.asistidos(), dias.programados(), dias.justificadas(),
                porcentaje, sinCultos, provisional);
    }

    @Transactional(readOnly = true)
    public List<FilaReporteResponse> reporte(YearMonth ym) {
        var conCulto = usaDias(ym) ? habilitacion.fechasConCulto(ym.atDay(1), ym.atEndOfMonth()) : null;
        return residentes.findByActivoTrueOrderByCodigoAsc().stream().map(r -> {
            String habitacion = asignaciones.findByResidenteIdAndHastaIsNull(r.getId())
                    .flatMap(a -> habitaciones.findById(a.getHabitacionId()).map(h -> h.getNumero() + "-" + a.getCama())).orElse("");
            String nombre = usuarios.findById(r.getUsuarioId()).orElseThrow().getNombreCompleto();
            return new FilaReporteResponse(r.getId(), r.getCodigo(), nombre, habitacion, resumen(r.getId(), ym, conCulto));
        }).toList();
    }

    /**
     * Excel del mes. Hoja «Cultos»: código universitario, nombre completo, días asistidos, días con culto programado, faltas justificadas,
     * porcentaje de asistencia y nota final (en meses anteriores a noviembre de 2026 los días y el porcentaje quedan vacíos y la nota es
     * la de la escala antigua). Hoja «Limpieza»: la de siempre, con habitación y tardanzas.
     */
    @Transactional(readOnly = true)
    public byte[] excel(YearMonth ym) {
        var filas = reporte(ym);
        boolean porDias = usaDias(ym);
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estilo = libro.createCellStyle();
            estilo.setFont(negrita);

            var cultos = libro.createSheet("Cultos " + ym);
            var titulo = cultos.createRow(0).createCell(0);
            titulo.setCellValue("Residencia Universitaria Fernando Stahl: notas de cultos de " + ym
                    + (porDias && !ym.atEndOfMonth().isBefore(reloj.instant().atZone(zona).toLocalDate()) ? " (nota a la fecha: el mes aún no termina)" : ""));
            titulo.setCellStyle(estilo);
            String[] cab1 = {"Código universitario", "Nombre completo", "Días asistidos", "Días con culto programado", "Faltas justificadas",
                    "Porcentaje de asistencia", "Nota final"};
            encabezado(cultos, 2, cab1, estilo);
            int fila = 3;
            for (var f : filas) {
                var x = cultos.createRow(fila++);
                var r = f.resumen();
                x.createCell(0).setCellValue(f.codigo());
                x.createCell(1).setCellValue(f.nombreCompleto());
                if (porDias) {
                    x.createCell(2).setCellValue(r.diasAsistidos());
                    x.createCell(3).setCellValue(r.diasConCulto());
                    x.createCell(4).setCellValue(r.faltasJustificadas());
                    if (r.porcentajeAsistencia() != null) {
                        x.createCell(5).setCellValue(Math.round(r.porcentajeAsistencia() * 10) / 10.0);
                    }
                }
                x.createCell(6).setCellValue(r.sinCultos() ? "Sin cultos en el mes" : r.notaCultos());
            }
            cultos.createRow(fila + 1).createCell(0).setCellValue(porDias
                    ? "Porcentaje = días asistidos / (días con culto programado - faltas justificadas), de lunes a viernes. AD 90 % o más · A 68 % o más · B 50 % o más · C menos de 50 %."
                    : "Mes anterior a noviembre de 2026: escala antigua por cantidad de cultos asistidos. AD 20 o más · A 15-19 · B 11-14 · C 0-10.");
            for (int i = 0; i < cab1.length; i++) {
                cultos.autoSizeColumn(i);
            }

            var limpieza = libro.createSheet("Limpieza " + ym);
            var titulo2 = limpieza.createRow(0).createCell(0);
            titulo2.setCellValue("Residencia Universitaria Fernando Stahl: limpieza de " + ym);
            titulo2.setCellStyle(estilo);
            String[] cab2 = {"N.°", "Código", "Apellidos y nombres", "Habitación", "Con tardanza", "Evaluaciones de limpieza", "Promedio de limpieza", "Nota de limpieza"};
            encabezado(limpieza, 2, cab2, estilo);
            int n = 3;
            for (var f : filas) {
                var x = limpieza.createRow(n);
                var r = f.resumen();
                x.createCell(0).setCellValue(n - 2);
                x.createCell(1).setCellValue(f.codigo());
                x.createCell(2).setCellValue(f.nombreCompleto());
                x.createCell(3).setCellValue(f.habitacion());
                x.createCell(4).setCellValue(r.tardanzas());
                x.createCell(5).setCellValue(r.evaluaciones());
                if (r.promedioLimpieza() == null) {
                    x.createCell(6).setCellValue("");
                    x.createCell(7).setCellValue("Sin evaluar");
                } else {
                    x.createCell(6).setCellValue(Math.round(r.promedioLimpieza() * 10) / 10.0);
                    x.createCell(7).setCellValue(r.notaLimpieza());
                }
                n++;
            }
            limpieza.createRow(n + 1).createCell(0).setCellValue("Limpieza: AD 18-20 · A 15-17 · B 11-14 · C 0-10.");
            for (int i = 0; i < cab2.length; i++) {
                limpieza.autoSizeColumn(i);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel", e);
        }
    }

    private static void encabezado(org.apache.poi.ss.usermodel.Sheet hoja, int fila, String[] titulos, CellStyle estilo) {
        var cab = hoja.createRow(fila);
        for (int i = 0; i < titulos.length; i++) {
            var c = cab.createCell(i);
            c.setCellValue(titulos[i]);
            c.setCellStyle(estilo);
        }
    }
}
