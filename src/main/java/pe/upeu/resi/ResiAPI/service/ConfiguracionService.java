package pe.upeu.resi.ResiAPI.service;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.dto.response.ConfiguracionResponse;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.ConfiguracionRepository;

/** Reglas institucionales configurables (varias están pendientes de confirmación; ver V1__esquema_inicial.sql). */
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository repositorio;
    private final AuditoriaService auditoria;

    public Optional<String> texto(String clave) {
        return repositorio.findById(clave).map(c -> c.getValor()).filter(v -> !v.isBlank());
    }

    public Optional<Long> numero(String clave) {
        return texto(clave).map(v -> Long.parseLong(v.trim()));
    }

    public boolean booleano(String clave, boolean porDefecto) {
        return texto(clave).map(v -> Boolean.parseBoolean(v.trim())).orElse(porDefecto);
    }

    public LocalTime hora(String clave) {
        return texto(clave).map(v -> LocalTime.parse(v.trim()))
                .orElseThrow(() -> new IllegalStateException("Falta la configuración " + clave));
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionResponse> listar() {
        return repositorio.findAll().stream()
                .map(c -> new ConfiguracionResponse(c.getClave(), c.getValor(), c.getDescripcion()))
                .sorted((a, b) -> a.clave().compareTo(b.clave())).toList();
    }

    @Transactional
    public ConfiguracionResponse actualizar(String clave, String valor, Long usuarioId) {
        var c = repositorio.findById(clave).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("La configuración no existe"));
        String limpio = valor == null || valor.isBlank() ? null : valor.trim();
        validarFormato(clave, limpio);
        c.setValor(limpio);
        auditoria.registrar(usuarioId, "CONFIG_ACTUALIZADA", "configuracion", null, clave + "=" + limpio);
        return new ConfiguracionResponse(c.getClave(), c.getValor(), c.getDescripcion());
    }

    private void validarFormato(String clave, String valor) {
        if (valor == null) {
            return;
        }
        try {
            if (clave.startsWith("culto.")) {
                LocalTime.parse(valor);
            } else if (clave.endsWith("max_horas") || clave.endsWith("anticipacion_horas") || clave.endsWith("vigencia_horas")) {
                if (Long.parseLong(valor) <= 0) {
                    throw new NumberFormatException();
                }
            } else if (!valor.equals("true") && !valor.equals("false")) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new Excepciones.SolicitudInvalida("El valor no tiene el formato esperado para " + clave);
        }
    }
}
