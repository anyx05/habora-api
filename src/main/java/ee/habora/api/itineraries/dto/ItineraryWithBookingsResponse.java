package ee.habora.api.itineraries.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ee.habora.api.bookings.dto.BookingResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ItineraryWithBookingsResponse(
        UUID id,
        UUID userId,
        String name,
        String status,
        String notes,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
        List<BookingResponse> bookings,
        BigDecimal totalEstimatedPrice
) {}
