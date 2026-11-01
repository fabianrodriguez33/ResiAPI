package pe.upeu.resi.ResiAPI.service;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.upeu.resi.ResiAPI.entity.Dispositivo;
import pe.upeu.resi.ResiAPI.entity.Notificacion;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.ApoderadoRepository;
import pe.upeu.resi.ResiAPI.repository.DispositivoRepository;
import pe.upeu.resi.ResiAPI.repository.NotificacionRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteApoderadoRepository;
import pe.upeu.resi.ResiAPI.repository.ResidenteRepository;
import pe.upeu.resi.ResiAPI.repository.UsuarioRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/**
 * Avisos de la residencia. Cada aviso queda en la bandeja del usuario y, cuando la transacción que lo originó se confirma,
 * sale por los otros tres canales: push al teléfono, correo (si está verificado) y SMS (si tiene teléfono).
 * Un fallo de un canal nunca afecta a la operación que lo generó ni a los demás canales.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    /** Lo que sale fuera de la aplicación. [usuarioId] puede ser null para un apoderado sin cuenta (solo correo y SMS). */
    public record Envio(Long usuarioId, String email, String telefono, String titulo, String mensaje) {
    }

    private final NotificacionRepository notificaciones;
    private final DispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;
    private final ResidenteRepository residentes;
    private final ResidenteApoderadoRepository vinculos;
    private final ApoderadoRepository apoderados;
    private final EnviadorCorreo correo;
    private final EnviadorSms sms;
    private final EnviadorPush push;
    private final ApplicationEventPublisher eventos;
    private final SesionActual sesion;
    private final Clock reloj;

    // ------------------------------------------------------------------ quién recibe

    @Transactional
    public void alUsuario(Long usuarioId, String titulo, String mensaje) {
        if (usuarioId == null) return;
        usuarios.findById(usuarioId).filter(u -> u.isActivo()).ifPresent(u -> {
            var n = new Notificacion();
            n.setUsuarioId(u.getId());
            n.setTitulo(recortar(titulo, 120));
            n.setMensaje(recortar(mensaje, 400));
            n.setCreadaEn(reloj.instant());
            notificaciones.save(n);
            eventos.publishEvent(new Envio(u.getId(), u.isEmailVerificado() ? u.getEmail() : null, u.getTelefono(), n.getTitulo(), n.getMensaje()));
        });
    }

    @Transactional
    public void alResidente(Long residenteId, String titulo, String mensaje) {
        residentes.findById(residenteId).ifPresent(r -> alUsuario(r.getUsuarioId(), titulo, mensaje));
    }

    /** Padres y apoderados del residente: con cuenta reciben todo; sin cuenta, solo correo y SMS a los datos que dejaron. */
    @Transactional
    public void aApoderadosDe(Long residenteId, String titulo, String mensaje) {
        for (var vinculo : vinculos.findByResidenteId(residenteId)) {
            apoderados.findById(vinculo.getApoderadoId()).ifPresent(a -> {
                if (a.getUsuarioId() != null) {
                    alUsuario(a.getUsuarioId(), titulo, mensaje);
                } else {
                    eventos.publishEvent(new Envio(null, a.getEmail(), a.getTelefono(), titulo, mensaje));
                }
            });
        }
    }

    @Transactional
    public void aRol(Rol rol, String titulo, String mensaje) {
        usuarios.findByRolAndActivoTrue(rol).forEach(u -> alUsuario(u.getId(), titulo, mensaje));
    }

    // ------------------------------------------------------------------ canales externos

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void despachar(Envio envio) {
        if (envio.usuarioId() != null) {
            for (Dispositivo d : dispositivos.findByUsuarioId(envio.usuarioId())) {
                try {
                    if (!push.notificar(d.getToken(), envio.titulo(), envio.mensaje())) {
                        dispositivos.deleteById(d.getId());
                    }
                } catch (RuntimeException e) {
                    log.error("Falló el push", e);
                }
            }
        }
        if (envio.email() != null && !envio.email().isBlank()) {
            try {
                correo.enviar(envio.email(), envio.titulo(), envio.mensaje());
            } catch (RuntimeException e) {
                log.error("Falló el correo", e);
            }
        }
        if (envio.telefono() != null && !envio.telefono().isBlank()) {
            try {
                sms.enviar(envio.telefono(), "ResiUP: " + envio.titulo() + ". " + envio.mensaje());
            } catch (RuntimeException e) {
                log.error("Falló el SMS", e);
            }
        }
    }

    // ------------------------------------------------------------------ bandeja del usuario actual

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Notificacion> mias(Pageable pageable) {
        return notificaciones.findByUsuarioIdOrderByCreadaEnDescIdDesc(sesion.obtener().id(), pageable);
    }

    @Transactional(readOnly = true)
    public long noLeidas() {
        return notificaciones.countByUsuarioIdAndLeidaFalse(sesion.obtener().id());
    }

    @Transactional
    public void marcarLeida(Long id) {
        var n = notificaciones.findById(id).filter(x -> x.getUsuarioId().equals(sesion.obtener().id()))
                .orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Notificación no encontrada"));
        n.setLeida(true);
    }

    @Transactional
    public void marcarTodasLeidas() {
        notificaciones.marcarTodasLeidas(sesion.obtener().id());
    }

    @Transactional
    public void registrarDispositivo(String token, String plataforma) {
        long yo = sesion.obtener().id();
        var existente = dispositivos.findByToken(token);
        if (existente.isPresent()) {
            existente.get().setUsuarioId(yo); // el teléfono cambió de dueño: el token pasa a la cuenta nueva
            return;
        }
        var d = new Dispositivo();
        d.setUsuarioId(yo);
        d.setToken(token);
        d.setPlataforma(plataforma);
        d.setCreadoEn(reloj.instant());
        dispositivos.save(d);
    }

    @Transactional
    public void olvidarDispositivo(String token) {
        dispositivos.findByToken(token).filter(d -> d.getUsuarioId().equals(sesion.obtener().id())).ifPresent(dispositivos::delete);
    }

    /** Nombre completo de un residente para armar los mensajes. */
    @Transactional(readOnly = true)
    public String nombreDe(Long residenteId) {
        return residentes.findById(residenteId).flatMap(r -> usuarios.findById(r.getUsuarioId())).map(u -> u.getNombreCompleto()).orElse("Un residente");
    }

    private static String recortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }

    /** Para pruebas y para el panel: lista corta de dispositivos de un usuario. */
    List<Dispositivo> dispositivosDe(Long usuarioId) {
        return dispositivos.findByUsuarioId(usuarioId);
    }
}
