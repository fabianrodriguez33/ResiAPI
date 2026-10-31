package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.response.CultoResponses.AsistenciaResponse;
import pe.upeu.resi.ResiAPI.dto.response.CultoResponses.ResidentePendienteResponse;
import pe.upeu.resi.ResiAPI.dto.response.CultoResponses.SesionCultoResponse;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.enums.TipoCulto;
import pe.upeu.resi.ResiAPI.service.CultoService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CultoController {

    public record AbrirSesionRequest(@NotNull(message = "El tipo de culto es obligatorio") TipoCulto tipo) {
    }

    public record RegistrarAsistenciaRequest(@NotBlank(message = "El QR es obligatorio") @Size(max = 120) String qr) {
    }

    public record AnularRequest(@NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo) {
    }

    private final CultoService cultos;

    @PostMapping("/cultos/sesiones")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public SesionCultoResponse abrir(@Valid @RequestBody AbrirSesionRequest request) {
        return cultos.abrir(request.tipo());
    }

    @GetMapping("/cultos/sesiones/hoy")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public List<SesionCultoResponse> hoy() {
        return cultos.deHoy();
    }

    @GetMapping("/cultos/sesiones/{id}")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public SesionCultoResponse obtener(@PathVariable Long id) {
        return cultos.obtener(id);
    }

    @PostMapping("/cultos/sesiones/{id}/asistencias")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public AsistenciaResponse registrar(@PathVariable Long id, @Valid @RequestBody RegistrarAsistenciaRequest request) {
        return cultos.registrar(id, request.qr());
    }

    @GetMapping("/cultos/sesiones/{id}/presentes")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public List<AsistenciaResponse> presentes(@PathVariable Long id) {
        return cultos.presentes(id);
    }

    @GetMapping("/cultos/sesiones/{id}/pendientes")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public List<ResidentePendienteResponse> pendientes(@PathVariable Long id) {
        return cultos.pendientes(id);
    }

    @PostMapping("/cultos/sesiones/{id}/cerrar")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public SesionCultoResponse cerrar(@PathVariable Long id) {
        return cultos.cerrar(id);
    }

    @PostMapping("/cultos/asistencias/{id}/anular")
    @PreAuthorize("hasAnyRole('PRECEPTOR','ADMINISTRADOR')")
    public void anular(@PathVariable Long id, @Valid @RequestBody AnularRequest request) {
        cultos.anular(id, request.motivo());
    }

    @GetMapping("/residentes/yo/asistencias")
    @PreAuthorize("hasRole('RESIDENTE')")
    public PaginaResponse<AsistenciaResponse> misAsistencias(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
        return cultos.deResidenteActual(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100)));
    }
}
