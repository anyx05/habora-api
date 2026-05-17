package ee.habora.api.itineraries.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AddBookingToItineraryRequest(
        @NotNull UUID berthId,
        @NotBlank @Size(max = 200) String customerName,
        @NotBlank @Email String customerEmail,
        @NotBlank @Size(max = 200) String vesselName,
        @NotNull @DecimalMin("0.1") @DecimalMax("200") BigDecimal vesselLengthM,
        @DecimalMin("0.1") @DecimalMax("30") BigDecimal vesselDraftM,
        @NotNull @FutureOrPresent LocalDate arrivalDate,
        @NotNull @Future LocalDate departureDate,
        @Size(max = 1000) String notes,
        Integer stopOrder
) {}
