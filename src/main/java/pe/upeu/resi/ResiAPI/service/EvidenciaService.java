package pe.upeu.resi.ResiAPI.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.upeu.resi.ResiAPI.config.AppProperties;
import pe.upeu.resi.ResiAPI.dto.response.PermisoResponses.EvidenciaResponse;
import pe.upeu.resi.ResiAPI.entity.Evidencia;
import pe.upeu.resi.ResiAPI.enums.Rol;
import pe.upeu.resi.ResiAPI.exception.Excepciones;
import pe.upeu.resi.ResiAPI.repository.EvidenciaRepository;
import pe.upeu.resi.ResiAPI.security.SesionActual;

/** Almacena fotografías de evidencia en disco (el directorio es configurable). Sólo se aceptan imágenes reales. */
@Service
@RequiredArgsConstructor
public class EvidenciaService {

    private final EvidenciaRepository evidencias;
    private final AppProperties props;
    private final SesionActual sesion;
    private final Clock reloj;

    public record Contenido(Resource recurso, String contentType) {
    }

    @Transactional
    public EvidenciaResponse subir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new Excepciones.SolicitudInvalida("Debe adjuntar una imagen");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new Excepciones.SolicitudInvalida("No se pudo leer el archivo");
        }
        String tipo = detectarTipo(bytes);
        if (tipo == null) {
            throw new Excepciones.SolicitudInvalida("Solo se aceptan imágenes JPEG, PNG o WebP");
        }
        String extension = switch (tipo) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
        // El nombre en disco es generado: el nombre original nunca forma parte de la ruta.
        Path directorio = Path.of(props.evidencias().directorio()).toAbsolutePath().normalize();
        String nombre = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(directorio);
            Files.write(directorio.resolve(nombre), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la evidencia", e);
        }
        var e = new Evidencia();
        e.setNombreOriginal(limpiarNombre(archivo.getOriginalFilename()));
        e.setContentType(tipo);
        e.setRuta(nombre);
        e.setTamanio(bytes.length);
        e.setSubidaPor(sesion.obtener().id());
        e.setCreadoEn(reloj.instant());
        e = evidencias.save(e);
        return new EvidenciaResponse(e.getId(), e.getNombreOriginal(), e.getContentType(), e.getTamanio());
    }

    /** Valida que la evidencia exista y haya sido subida por el usuario indicado. */
    @Transactional(readOnly = true)
    public Evidencia exigirPropia(Long evidenciaId, Long usuarioId) {
        var e = evidencias.findById(evidenciaId).orElseThrow(() -> new Excepciones.SolicitudInvalida("La evidencia indicada no existe"));
        if (!e.getSubidaPor().equals(usuarioId)) {
            throw new Excepciones.SolicitudInvalida("La evidencia indicada no existe");
        }
        return e;
    }

    @Transactional(readOnly = true)
    public Contenido obtener(Long id) {
        var e = evidencias.findById(id).orElseThrow(() -> new Excepciones.RecursoNoEncontrado("Evidencia no encontrada"));
        var actual = sesion.obtener();
        if (actual.rol() != Rol.PRECEPTOR && actual.rol() != Rol.ADMINISTRADOR && !e.getSubidaPor().equals(actual.id())) {
            throw new Excepciones.Prohibido("Sin acceso a la evidencia");
        }
        Path archivo = Path.of(props.evidencias().directorio()).toAbsolutePath().normalize().resolve(e.getRuta());
        if (!Files.exists(archivo)) {
            throw new Excepciones.RecursoNoEncontrado("El archivo de la evidencia no está disponible");
        }
        return new Contenido(new FileSystemResource(archivo), e.getContentType());
    }

    private static String detectarTipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String limpiarNombre(String nombre) {
        String base = nombre == null ? "evidencia" : nombre.replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 100 ? base.substring(0, 100) : base;
    }
}
