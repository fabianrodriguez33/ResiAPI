package pe.upeu.resi.ResiAPI.dto.response;

import java.util.Map;

public record ErrorResponse(int status, String error, String message, Map<String, String> validationErrors) {

    public static ErrorResponse de(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null);
    }
}
