package ee.habora.api.itineraries.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConfirmedBookingResponse(
        UUID bookingId,
        String confirmationCode,
        UUID berthId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate arrivalDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate departureDate,
        BigDecimal totalPrice
) {}
