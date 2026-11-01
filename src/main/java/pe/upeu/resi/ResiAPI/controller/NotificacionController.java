package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upeu.resi.ResiAPI.dto.response.PaginaResponse;
import pe.upeu.resi.ResiAPI.service.NotificacionService;

/** Bandeja de notificaciones del usuario autenticado (cualquier rol) y registro de su teléfono para el push. */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    public record NotificacionResponse(Long id, String titulo, String mensaje, boolean leida, Instant creadaEn) {
    }

    public record NoLeidasResponse(long cantidad) {
    }

    public record DispositivoRequest(@NotBlank @Size(max = 400) String token,
                                     @NotBlank @Pattern(regexp = "ANDROID|IOS") String plataforma) {
    }

    private final NotificacionService notificaciones;

    @GetMapping
    public PaginaResponse<NotificacionResponse> mias(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "30") int tamanio) {
        var pageable = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100));
        return PaginaResponse.de(notificaciones.mias(pageable),
                n -> new NotificacionResponse(n.getId(), n.getTitulo(), n.getMensaje(), n.isLeida(), n.getCreadaEn()));
    }

    @GetMapping("/no-leidas")
    public NoLeidasResponse noLeidas() {
        return new NoLeidasResponse(notificaciones.noLeidas());
    }

    @PostMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLeida(@PathVariable Long id) {
        notificaciones.marcarLeida(id);
    }

    @PostMapping("/leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarTodas() {
        notificaciones.marcarTodasLeidas();
    }

    @PostMapping("/dispositivos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarDispositivo(@Valid @RequestBody DispositivoRequest r) {
        notificaciones.registrarDispositivo(r.token().trim(), r.plataforma());
    }

    @DeleteMapping("/dispositivos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void olvidarDispositivo(@RequestParam String token) {
        notificaciones.olvidarDispositivo(token);
    }
}
