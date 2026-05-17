package ee.habora.api.itineraries;

import ee.habora.api.bookings.dto.BookingResponse;
import ee.habora.api.itineraries.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/itineraries")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Itineraries")
public class ItineraryController {

    private final ItineraryService service;

    ItineraryController(ItineraryService service) {
        this.service = service;
    }

    @Operation(summary = "Create a new draft itinerary")
    @PostMapping
    public ResponseEntity<ItineraryResponse> createItinerary(
            @Valid @RequestBody CreateItineraryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createItinerary(userId, request));
    }

    @Operation(summary = "Get the authenticated user's current draft itinerary with nested bookings")
    @GetMapping("/current")
    public ResponseEntity<ItineraryWithBookingsResponse> getCurrent(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        return ResponseEntity.ok(service.getCurrentDraft(userId));
    }

    @Operation(summary = "Get a specific itinerary with nested bookings (must be owned by caller)")
    @GetMapping("/{id}")
    public ResponseEntity<ItineraryWithBookingsResponse> getItinerary(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        return ResponseEntity.ok(service.getItinerary(id, userId));
    }

    @Operation(summary = "Add a pending booking to a draft itinerary")
    @PostMapping("/{id}/bookings")
    public ResponseEntity<BookingResponse> addBooking(
            @PathVariable UUID id,
            @Valid @RequestBody AddBookingToItineraryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        String email = resolveEmail(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addBookingToItinerary(id, userId, email, request));
    }

    @Operation(summary = "Confirm all pending bookings in a draft itinerary")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ConfirmItineraryResponse> confirmItinerary(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        String email = resolveEmail(jwt);
        return ResponseEntity.ok(service.confirmItinerary(id, userId, email));
    }

    @Operation(summary = "Remove a pending booking from a draft itinerary")
    @DeleteMapping("/{id}/bookings/{bookingId}")
    public ResponseEntity<Void> removeBooking(
            @PathVariable UUID id,
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        service.removeBookingFromItinerary(id, bookingId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cancel a draft itinerary (also cancels its pending bookings)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ItineraryResponse> cancelItinerary(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        return ResponseEntity.ok(service.cancelItinerary(id, userId));
    }

    private static UUID resolveUserId(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "JWT is missing the 'sub' claim");
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "JWT 'sub' claim is not a valid UUID");
        }
    }

    private static String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "JWT is missing the 'email' claim");
        }
        return email;
    }
}
