package ee.habora.api.itineraries.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateItineraryRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String notes
) {}
