package ee.habora.api.common.exception;

public class BookingException extends ApiException {

    private final BookingErrorCode errorCode;

    public BookingException(BookingErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BookingErrorCode getErrorCode() {
        return errorCode;
    }

    public enum BookingErrorCode {
        BERTH_NOT_FOUND,             // P0001 → 404
        VESSEL_TOO_LONG,             // P0002 → 422
        VESSEL_TOO_DEEP,             // P0003 → 422
        BERTH_UNAVAILABLE,           // P0004 → 409
        ITINERARY_NOT_FOUND,         // P0010 → 404
        ITINERARY_NOT_DRAFT,         // P0011 → 409
        BERTH_CONFLICT_IN_ITINERARY  // P0012 → 409
    }
}
