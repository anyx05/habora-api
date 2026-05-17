package ee.habora.api.bookings;

import ee.habora.api.bookings.dto.BookingResponse;
import ee.habora.api.bookings.dto.CancelBookingResponse;
import ee.habora.api.bookings.dto.CreateBookingRequest;
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
@RequestMapping("/api/v1/bookings")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookingService;

    BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Create a berth booking")
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String email = resolveEmail(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(request, email));
    }

    @Operation(summary = "Cancel a booking (caller must own it)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<CancelBookingResponse> cancelBooking(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String email = resolveEmail(jwt);
        return ResponseEntity.ok(bookingService.cancelBooking(id, email));
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
