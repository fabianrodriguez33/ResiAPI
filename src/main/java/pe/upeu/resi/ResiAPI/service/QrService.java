package pe.upeu.resi.ResiAPI.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.resi.ResiAPI.config.AppProperties;
import pe.upeu.resi.ResiAPI.dto.response.ResidenteResponses.QrResponse;
import pe.upeu.resi.ResiAPI.entity.QrResidente;
import pe.upeu.resi.ResiAPI.entity.Residente;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.QrResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;

/**
 * QR personal. El contenido es «RESIUP1.&lt;uuid&gt;.&lt;firma&gt;»: un identificador opaco más una firma HMAC emitida por el
 * servidor. No contiene datos personales ni permisos. El servidor decide siempre si es válido, está revocado y si el
 * residente puede operar; leer el QR nunca autoriza nada por sí solo.
 */
@Service
@RequiredArgsConstructor
public class QrService {

    private static final String PREFIJO = "RESIUP1";

    public enum Estado { VALIDO, INVALIDO, REVOCADO }

    public record Resolucion(Estado estado, Residente residente) {
    }

    private final QrResidenteRepository qrs;
    private final ResidenteRepository residentes;
    private final AppProperties props;
    private final Clock reloj;
    private final AuditoriaService auditoria;

    @Transactional
    public QrResidente emitir(Long residenteId) {
        var q = new QrResidente();
        q.setResidenteId(residenteId);
        q.setCodigo(UUID.randomUUID());
        q.setActivo(true);
        q.setCreadoEn(reloj.instant());
        return qrs.save(q);
    }

    @Transactional(readOnly = true)
    public QrResponse contenidoVigente(Long residenteId) {
        var q = qrs.findByResidenteIdAndActivoTrue(residenteId)
                .orElseThrow(() -> new Excepciones.RecursoNoEncontrado("El residente no tiene un QR activo"));
        return new QrResponse(contenido(q.getCodigo()), q.getCreadoEn());
    }

    /** Revoca el QR vigente y emite uno nuevo (p. ej. por pérdida o captura filtrada). */
    @Transactional
    public QrResponse regenerar(Long residenteId, Long actorId) {
        residentes.findByIdForUpdate(residenteId).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Residente no encontrado"));
        qrs.findByResidenteIdAndActivoTrue(residenteId).ifPresent(q -> {
            q.setActivo(false);
            q.setRevocadoEn(reloj.instant());
            qrs.saveAndFlush(q);
        });
        var nuevo = emitir(residenteId);
        auditoria.registrar(actorId, "QR_REGENERADO", "residente", residenteId, null);
        return new QrResponse(contenido(nuevo.getCodigo()), nuevo.getCreadoEn());
    }

    @Transactional(readOnly = true)
    public Resolucion resolver(String contenido) {
        if (contenido == null || contenido.length() > 120) {
            return new Resolucion(Estado.INVALIDO, null);
        }
        String[] partes = contenido.trim().split("\\.");
        if (partes.length != 3 || !PREFIJO.equals(partes[0])) {
            return new Resolucion(Estado.INVALIDO, null);
        }
        UUID codigo;
        try {
            codigo = UUID.fromString(partes[1]);
        } catch (IllegalArgumentException e) {
            return new Resolucion(Estado.INVALIDO, null);
        }
        byte[] esperada = firma(codigo);
        byte[] recibida;
        try {
            recibida = Base64.getUrlDecoder().decode(partes[2]);
        } catch (IllegalArgumentException e) {
            return new Resolucion(Estado.INVALIDO, null);
        }
        if (!MessageDigest.isEqual(esperada, recibida)) {
            return new Resolucion(Estado.INVALIDO, null);
        }
        var qr = qrs.findByCodigo(codigo).orElse(null);
        if (qr == null) {
            return new Resolucion(Estado.INVALIDO, null);
        }
        var residente = residentes.findById(qr.getResidenteId()).orElse(null);
        return new Resolucion(qr.isActivo() ? Estado.VALIDO : Estado.REVOCADO, residente);
    }

    String contenido(UUID codigo) {
        return PREFIJO + "." + codigo + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(firma(codigo));
    }

    private byte[] firma(UUID codigo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.qr().secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Arrays.copyOf(mac.doFinal(("qr:" + codigo).getBytes(StandardCharsets.UTF_8)), 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el QR", e);
        }
    }
}
