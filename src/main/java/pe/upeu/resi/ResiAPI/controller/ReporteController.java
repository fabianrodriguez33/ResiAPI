package pe.upeu.resi.ResiAPI.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.response.ReporteResponses.FilaReporteResponse;
import pe.upeu.resi.ResiAPI.dto.response.ReporteResponses.ResumenMensualResponse;
import pe.upeu.resi.ResiAPI.service.NotasService;
import pe.upeu.resi.ResiAPI.service.ResidenteService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReporteController {

    private final NotasService notas;
    private final ResidenteService residentes;

    /** Cómo le fue al residente en el mes (sin mes: el actual). */
    @GetMapping("/residentes/yo/resumen-mensual")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResumenMensualResponse miResumen(@RequestParam(required = false) Integer anio, @RequestParam(required = false) Integer mes) {
        return notas.resumen(residentes.residenteActual().getId(), notas.mes(anio, mes));
    }

    @GetMapping("/reportes/mensual")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public List<FilaReporteResponse> reporte(@RequestParam(required = false) Integer anio, @RequestParam(required = false) Integer mes) {
        return notas.reporte(notas.mes(anio, mes));
    }

    @GetMapping("/reportes/mensual.xlsx")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) Integer anio, @RequestParam(required = false) Integer mes) {
        var ym = notas.mes(anio, mes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("notas-" + ym + ".xlsx").build().toString())
                .body(notas.excel(ym));
    }
}
