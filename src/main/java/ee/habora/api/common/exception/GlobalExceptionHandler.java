package ee.habora.api.common.exception;

import ee.habora.api.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BookingException.class)
    ResponseEntity<ErrorResponse> handleBookingException(BookingException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case BERTH_NOT_FOUND, ITINERARY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VESSEL_TOO_LONG, VESSEL_TOO_DEEP -> HttpStatus.UNPROCESSABLE_ENTITY;
            case BERTH_UNAVAILABLE, ITINERARY_NOT_DRAFT, BERTH_CONFLICT_IN_ITINERARY -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(),
                        ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (first, second) -> first
                ));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withFields(400, "Bad Request", "Validation failed",
                        request.getRequestURI(), fields));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        if (status == HttpStatus.NOT_FOUND) {
            log.debug("404 on {} {}: {}", request.getMethod(), request.getRequestURI(), reason);
        } else if (status.is4xxClientError()) {
            log.warn("Client error {} on {} {}: {}", status.value(), request.getMethod(), request.getRequestURI(), reason);
        } else {
            log.error("Server error {} on {} {}", status.value(), request.getMethod(), request.getRequestURI(), ex);
        }
        String errorPhrase = status instanceof HttpStatus hs ? hs.getReasonPhrase() : "Error";
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), errorPhrase, reason, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred", request.getRequestURI()));
    }
}
