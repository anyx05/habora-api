package ee.habora.api.itineraries.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ConfirmItineraryResponse(
        UUID itineraryId,
        String status,
        List<ConfirmedBookingResponse> confirmedBookings,
        BigDecimal totalPrice
) {}
