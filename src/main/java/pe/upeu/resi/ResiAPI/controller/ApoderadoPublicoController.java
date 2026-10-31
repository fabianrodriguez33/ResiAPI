package pe.upeu.resi.ResiAPI.controller;

import jakarta.validation.Valid;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import pe.upeu.resi.ResiAPI.dto.request.PermisoDtos.DecisionApoderadoRequest;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.SolicitudApoderadoResponse;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.service.PermisoService;

/**
 * Aprobación del apoderado sin instalar la app. El token del enlace es de un solo uso y expira; la URL no contiene datos
 * personales. Expone una API JSON y una página web mínima.
 */
@RestController
@RequiredArgsConstructor
public class ApoderadoPublicoController {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, h:mm a", Locale.forLanguageTag("es-PE"));

    private final PermisoService permisos;
    private final ZoneId zona;

    @GetMapping("/api/v1/publico/apoderado/{token}")
    public SolicitudApoderadoResponse consultar(@PathVariable String token) {
        return permisos.consultarParaApoderado(token);
    }

    @PostMapping("/api/v1/publico/apoderado/{token}")
    public void decidir(@PathVariable String token, @Valid @RequestBody DecisionApoderadoRequest request) {
        permisos.decidirApoderado(token, request.aprobar());
    }

    @GetMapping(value = "/publico/aprobacion/{token}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> pagina(@PathVariable String token) {
        try {
            var s = permisos.consultarParaApoderado(token);
            String cuerpo = "<h1>Autorización de permiso de salida</h1>"
                    + "<p><b>" + e(s.residente()) + "</b> solicita un permiso " + e(s.tipo().toLowerCase()) + ".</p>"
                    + "<ul><li>Motivo: " + e(s.motivo()) + "</li><li>Destino: " + e(s.destino()) + "</li>"
                    + "<li>Salida: " + fecha(s.salida()) + "</li><li>Retorno: " + fecha(s.retorno()) + "</li></ul>"
                    + "<p>Su respuesta se enviará a la residencia, que revisará la solicitud.</p>"
                    + "<form method='post'><button name='decision' value='aprobar' class='ok'>Autorizar</button> "
                    + "<button name='decision' value='rechazar' class='no'>Rechazar</button></form>";
            return html(HttpStatus.OK, cuerpo);
        } catch (Excepciones.RecursoNoEncontrado | Excepciones.Conflicto ex) {
            return html(HttpStatus.GONE, "<h1>Enlace no disponible</h1><p>" + e(ex.getMessage()) + ".</p>");
        }
    }

    @PostMapping(value = "/publico/aprobacion/{token}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> responder(@PathVariable String token, @RequestParam String decision) {
        try {
            permisos.decidirApoderado(token, "aprobar".equals(decision));
            return html(HttpStatus.OK, "<h1>Gracias</h1><p>Su respuesta fue registrada. Ya puede cerrar esta página.</p>");
        } catch (Excepciones.RecursoNoEncontrado | Excepciones.Conflicto ex) {
            return html(HttpStatus.GONE, "<h1>Enlace no disponible</h1><p>" + e(ex.getMessage()) + ".</p>");
        }
    }

    private String fecha(java.time.Instant i) {
        return e(FORMATO.format(i.atZone(zona)));
    }

    private static String e(String texto) {
        return HtmlUtils.htmlEscape(texto == null ? "" : texto);
    }

    private static ResponseEntity<String> html(HttpStatus estado, String cuerpo) {
        String pagina = "<!doctype html><html lang='es'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>ResiUP</title><style>body{font-family:system-ui,sans-serif;max-width:480px;margin:2rem auto;padding:0 1rem;color:#111827}"
                + "button{font-size:1rem;padding:.8rem 1.4rem;border:0;border-radius:12px;color:#fff;margin-right:.5rem}.ok{background:#0A2F6B}.no{background:#C62828}</style>"
                + "</head><body>" + cuerpo + "</body></html>";
        return ResponseEntity.status(estado).header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").body(pagina);
    }
}
