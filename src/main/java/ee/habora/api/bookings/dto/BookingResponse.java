package ee.habora.api.bookings.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID berthId,
        String customerName,
        String customerEmail,
        String vesselName,
        BigDecimal vesselLengthM,
        BigDecimal vesselDraftM,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate arrivalDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate departureDate,
        String status,
        BigDecimal totalPrice,
        String notes,
        String confirmationCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {}
