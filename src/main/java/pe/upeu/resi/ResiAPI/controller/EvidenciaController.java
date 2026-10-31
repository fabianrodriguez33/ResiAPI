package pe.upeu.resi.ResiAPI.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.EvidenciaResponse;
import pe.upeu.resi.ResiAPI.service.EvidenciaService;

@RestController
@RequestMapping("/api/v1/evidencias")
@RequiredArgsConstructor
public class EvidenciaController {

    private final EvidenciaService evidencias;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('RESIDENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenciaResponse subir(@RequestParam("archivo") MultipartFile archivo) {
        return evidencias.subir(archivo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RESIDENTE','PRECEPTOR','ADMINISTRADOR')")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) {
        var c = evidencias.obtener(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.contentType()))
                .header("X-Content-Type-Options", "nosniff").body(c.recurso());
    }
}
