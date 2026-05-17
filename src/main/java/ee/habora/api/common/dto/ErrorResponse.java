package ee.habora.api.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse withFields(int status, String error, String message,
                                           String path, Map<String, String> fields) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fields);
    }
}
