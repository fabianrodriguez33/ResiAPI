package pe.upeu.resi.ResiAPI.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.upeu.resi.ResiAPI.dto.response.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<ErrorResponse> responder(HttpStatus estado, String mensaje, Map<String, String> errores) {
        return ResponseEntity.status(estado).body(new ErrorResponse(estado.value(), estado.getReasonPhrase(), mensaje, errores));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errores.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return responder(HttpStatus.BAD_REQUEST, "Datos inválidos", errores);
    }

    @ExceptionHandler(Excepciones.SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> invalida(Excepciones.SolicitudInvalida e) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage(), e.getErrores());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> cuerpoInvalido(Exception e) {
        return responder(HttpStatus.BAD_REQUEST, "La solicitud tiene un formato inválido", null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ErrorResponse> credenciales(BadCredentialsException e) {
        return responder(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", null);
    }

    @ExceptionHandler(Excepciones.DemasiadosIntentos.class)
    ResponseEntity<ErrorResponse> demasiadosIntentos(Excepciones.DemasiadosIntentos e) {
        var estado = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(estado).header("Retry-After", String.valueOf(e.getSegundos()))
                .body(new ErrorResponse(estado.value(), estado.getReasonPhrase(), e.getMessage(), null));
    }

    @ExceptionHandler({AccessDeniedException.class, Excepciones.Prohibido.class})
    ResponseEntity<ErrorResponse> prohibido(RuntimeException e) {
        return responder(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción", null);
    }

    @ExceptionHandler(Excepciones.RecursoNoEncontrado.class)
    ResponseEntity<ErrorResponse> noEncontrado(Excepciones.RecursoNoEncontrado e) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException e) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> metodo(HttpRequestMethodNotSupportedException e) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tamanio(MaxUploadSizeExceededException e) {
        return responder(HttpStatus.CONTENT_TOO_LARGE, "El archivo supera el tamaño permitido (5 MB)", null);
    }

    @ExceptionHandler({Excepciones.Conflicto.class, DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ErrorResponse> conflicto(RuntimeException e) {
        String mensaje = e instanceof Excepciones.Conflicto ? e.getMessage()
                : "La operación entra en conflicto con el estado actual de los datos";
        if (!(e instanceof Excepciones.Conflicto)) {
            log.warn("Conflicto de datos: {}", e.getClass().getSimpleName());
        }
        return responder(HttpStatus.CONFLICT, mensaje, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> inesperado(Exception e) {
        log.error("Error no controlado", e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno", null);
    }
}
