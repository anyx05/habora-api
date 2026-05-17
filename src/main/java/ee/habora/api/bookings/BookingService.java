package ee.habora.api.bookings;

import ee.habora.api.bookings.dto.BookingResponse;
import ee.habora.api.bookings.dto.CancelBookingResponse;
import ee.habora.api.bookings.dto.CreateBookingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

// Public to allow injection from the itineraries package (cross-domain delegation).
@Service
public class BookingService {

    private final BookingJdbcRepository repository;

    BookingService(BookingJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional
    BookingResponse createBooking(CreateBookingRequest request, String authenticatedEmail) {
        // authenticatedEmail is always taken from the JWT — the DTO's customerEmail is
        // for display/validation only and is overridden here to prevent spoofing.
        return repository.create(request, authenticatedEmail);
    }

    @Transactional
    public BookingResponse createBookingInItinerary(CreateBookingRequest request,
                                                    String authenticatedEmail,
                                                    UUID itineraryId,
                                                    Integer stopOrder) {
        return repository.createInItinerary(request, authenticatedEmail, itineraryId, stopOrder);
    }

    @Transactional
    CancelBookingResponse cancelBooking(UUID id, String authenticatedEmail) {
        int updated = repository.cancel(id, authenticatedEmail);
        if (updated == 0) {
            // Intentionally ambiguous: don't reveal whether the booking exists at all.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Booking not found or access denied");
        }
        return new CancelBookingResponse(id, "cancelled", "Booking successfully cancelled");
    }
}
