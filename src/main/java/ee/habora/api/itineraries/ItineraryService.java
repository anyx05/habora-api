package ee.habora.api.itineraries;

import ee.habora.api.bookings.BookingService;
import ee.habora.api.bookings.dto.BookingResponse;
import ee.habora.api.bookings.dto.CreateBookingRequest;
import ee.habora.api.itineraries.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class ItineraryService {

    private final ItineraryJdbcRepository repository;
    private final BookingService bookingService;

    ItineraryService(ItineraryJdbcRepository repository, BookingService bookingService) {
        this.repository = repository;
        this.bookingService = bookingService;
    }

    @Transactional
    ItineraryResponse createItinerary(UUID userId, CreateItineraryRequest request) {
        String name = (request.name() != null && !request.name().isBlank())
                ? request.name()
                : "My Trip";
        return repository.insertItinerary(userId, name, request.notes());
    }

    @Transactional(readOnly = true)
    ItineraryWithBookingsResponse getCurrentDraft(UUID userId) {
        return repository.findCurrentDraftWithBookings(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No draft itinerary found"));
    }

    @Transactional(readOnly = true)
    ItineraryWithBookingsResponse getItinerary(UUID id, UUID userId) {
        return repository.findByIdWithBookings(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied"));
    }

    @Transactional
    BookingResponse addBookingToItinerary(UUID itineraryId, UUID userId, String userEmail,
                                          AddBookingToItineraryRequest request) {
        ItineraryWithBookingsResponse itinerary = repository.findByIdWithBookings(itineraryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        if (!"draft".equals(itinerary.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Itinerary is not in draft status");
        }

        int stopOrder = (request.stopOrder() != null)
                ? request.stopOrder()
                : repository.findMaxStopOrder(itineraryId) + 1;

        // customerEmail from the DTO is intentionally ignored — userEmail from JWT is authoritative.
        CreateBookingRequest bookingReq = new CreateBookingRequest(
                request.berthId(), request.customerName(), userEmail,
                request.vesselName(), request.vesselLengthM(), request.vesselDraftM(),
                request.arrivalDate(), request.departureDate(), request.notes()
        );
        return bookingService.createBookingInItinerary(bookingReq, userEmail, itineraryId, stopOrder);
    }

    @Transactional
    ConfirmItineraryResponse confirmItinerary(UUID itineraryId, UUID userId, String userEmail) {
        repository.findByIdWithBookings(itineraryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        List<ConfirmedBookingResponse> confirmed = repository.confirmItinerary(itineraryId, userEmail);
        BigDecimal total = confirmed.stream()
                .map(ConfirmedBookingResponse::totalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ConfirmItineraryResponse(itineraryId, "confirmed", confirmed, total);
    }

    @Transactional
    ItineraryResponse cancelItinerary(UUID id, UUID userId) {
        ItineraryWithBookingsResponse itinerary = repository.findByIdWithBookings(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        if (!"draft".equals(itinerary.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only draft itineraries can be cancelled");
        }
        repository.cancelItinerary(id, userId);
        return new ItineraryResponse(itinerary.id(), itinerary.userId(), itinerary.name(),
                "cancelled", itinerary.notes(), itinerary.createdAt(), Instant.now());
    }

    @Transactional
    void removeBookingFromItinerary(UUID itineraryId, UUID bookingId, UUID userId) {
        ItineraryWithBookingsResponse itinerary = repository.findByIdWithBookings(itineraryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        if (!"draft".equals(itinerary.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Itinerary is not in draft status");
        }
        int deleted = repository.deleteBookingFromItinerary(itineraryId, bookingId, userId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Booking not found in this itinerary");
        }
    }
}
