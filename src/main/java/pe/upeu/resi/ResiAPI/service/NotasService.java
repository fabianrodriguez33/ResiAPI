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
 * Notas mensuales. Cultos (cantidad de asistencias en el mes): AD 20 o más, A 15-19, B 11-14, C 0-10.
 * Limpieza (promedio del mes sobre 20, redondeado): AD 18-20, A 15-17, B 11-14, C 0-10.
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
    private final Clock reloj;
    private final ZoneId zona;

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
        var desde = ym.atDay(1);
        var hasta = ym.atEndOfMonth();
        long cultos = asistencias.contarEnPeriodo(residenteId, desde, hasta);
        long tardanzas = asistencias.contarTardanzasEnPeriodo(residenteId, desde, hasta);
        Object[] fila = evaluaciones.estadisticas(residenteId, desde, hasta).get(0);
        long n = ((Number) fila[0]).longValue();
        Double promedio = fila[1] == null ? null : ((Number) fila[1]).doubleValue();
        return new ResumenMensualResponse(ym.getYear(), ym.getMonthValue(), cultos, notaCultos(cultos), tardanzas, n,
                promedio, promedio == null ? null : notaLimpieza(promedio));
    }

    @Transactional(readOnly = true)
    public List<FilaReporteResponse> reporte(YearMonth ym) {
        return residentes.findByActivoTrueOrderByCodigoAsc().stream().map(r -> {
            String habitacion = asignaciones.findByResidenteIdAndHastaIsNull(r.getId())
                    .flatMap(a -> habitaciones.findById(a.getHabitacionId()).map(h -> h.getNumero() + "-" + a.getCama())).orElse("");
            String nombre = usuarios.findById(r.getUsuarioId()).orElseThrow().getNombreCompleto();
            return new FilaReporteResponse(r.getId(), r.getCodigo(), nombre, habitacion, resumen(r.getId(), ym));
        }).toList();
    }

    /** Excel con las notas de todos los internos del mes: cultos y limpieza. */
    @Transactional(readOnly = true)
    public byte[] excel(YearMonth ym) {
        var filas = reporte(ym);
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Notas " + ym);
            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estilo = libro.createCellStyle();
            estilo.setFont(negrita);
            var titulo = hoja.createRow(0).createCell(0);
            titulo.setCellValue("Residencia Universitaria Fernando Stahl: notas de " + ym);
            titulo.setCellStyle(estilo);
            String[] cabecera = {"N.°", "Código", "Apellidos y nombres", "Habitación", "Cultos asistidos", "Nota de cultos", "Con tardanza",
                    "Evaluaciones de limpieza", "Promedio de limpieza", "Nota de limpieza"};
            var cab = hoja.createRow(2);
            for (int i = 0; i < cabecera.length; i++) {
                var c = cab.createCell(i);
                c.setCellValue(cabecera[i]);
                c.setCellStyle(estilo);
            }
            int fila = 3;
            for (var f : filas) {
                var x = hoja.createRow(fila);
                var r = f.resumen();
                x.createCell(0).setCellValue(fila - 2);
                x.createCell(1).setCellValue(f.codigo());
                x.createCell(2).setCellValue(f.nombreCompleto());
                x.createCell(3).setCellValue(f.habitacion());
                x.createCell(4).setCellValue(r.cultos());
                x.createCell(5).setCellValue(r.notaCultos());
                x.createCell(6).setCellValue(r.tardanzas());
                x.createCell(7).setCellValue(r.evaluaciones());
                if (r.promedioLimpieza() == null) {
                    x.createCell(8).setCellValue("");
                    x.createCell(9).setCellValue("Sin evaluar");
                } else {
                    x.createCell(8).setCellValue(Math.round(r.promedioLimpieza() * 10) / 10.0);
                    x.createCell(9).setCellValue(r.notaLimpieza());
                }
                fila++;
            }
            hoja.createRow(fila + 1).createCell(0).setCellValue("Cultos: AD 20 o más · A 15-19 · B 11-14 · C 0-10.   Limpieza: AD 18-20 · A 15-17 · B 11-14 · C 0-10.");
            for (int i = 0; i < cabecera.length; i++) {
                hoja.autoSizeColumn(i);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel", e);
        }
    }
}
