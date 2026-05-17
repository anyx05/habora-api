package ee.habora.api.itineraries.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record ItineraryResponse(
        UUID id,
        UUID userId,
        String name,
        String status,
        String notes,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {}
