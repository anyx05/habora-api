package ee.habora.api.bookings.dto;

import java.util.UUID;

public record CancelBookingResponse(
        UUID id,
        String status,
        String message
) {}
