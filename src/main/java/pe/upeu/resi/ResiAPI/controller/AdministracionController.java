package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.response.AuditoriaResponse;
import pe.upeu.resi.ResiAPI.dto.response.ConfiguracionResponse;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.security.SesionActual;
import pe.upeu.resi.ResiAPI.service.AuditoriaService;
import pe.upeu.resi.ResiAPI.service.ConfiguracionService;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@RequiredArgsConstructor
public class AdministracionController {

    public record ConfiguracionRequest(@Size(max = 200) String valor) {
    }

    private final AuditoriaService auditoria;
    private final ConfiguracionService configuracion;
    private final SesionActual sesion;

    @GetMapping("/auditoria")
    public PaginaResponse<AuditoriaResponse> auditoria(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanio) {
        return auditoria.listar(PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 200)));
    }

    @GetMapping("/configuracion")
    public List<ConfiguracionResponse> listarConfiguracion() {
        return configuracion.listar();
    }

    @PutMapping("/configuracion/{clave}")
    public ConfiguracionResponse actualizarConfiguracion(@PathVariable String clave, @Valid @RequestBody ConfiguracionRequest request) {
        return configuracion.actualizar(clave, request.valor(), sesion.obtener().id());
    }
}
