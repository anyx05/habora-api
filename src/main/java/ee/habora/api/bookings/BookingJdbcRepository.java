package ee.habora.api.bookings;

import ee.habora.api.bookings.dto.BookingResponse;
import ee.habora.api.bookings.dto.CreateBookingRequest;
import ee.habora.api.common.exception.BookingException;
import ee.habora.api.common.exception.BookingException.BookingErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

// PSQLException is runtimeOnly — use java.sql.SQLException to read SQLState portably.

@Repository
class BookingJdbcRepository {

    private static final String CREATE_SQL = """
            SELECT * FROM create_booking_safely(
                :p_berth_id, :p_customer_name, :p_customer_email, :p_vessel_name,
                :p_vessel_length_m, :p_vessel_draft_m, :p_arrival_date, :p_departure_date,
                :p_notes
            )
            """;

    private static final String CREATE_IN_ITINERARY_SQL = """
            SELECT * FROM create_booking_safely(
                :p_berth_id, :p_customer_name, :p_customer_email, :p_vessel_name,
                :p_vessel_length_m, :p_vessel_draft_m, :p_arrival_date, :p_departure_date,
                :p_notes, :p_itinerary_id, :p_stop_order
            )
            """;

    private static final String CANCEL_SQL = """
            UPDATE bookings
               SET status = 'cancelled'
             WHERE id = :id
               AND customer_email = :email
               AND status != 'cancelled'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    BookingJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    BookingResponse create(CreateBookingRequest req, String customerEmail) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("p_berth_id", req.berthId())
                .addValue("p_customer_name", req.customerName())
                .addValue("p_customer_email", customerEmail)
                .addValue("p_vessel_name", req.vesselName())
                .addValue("p_vessel_length_m", req.vesselLengthM())
                .addValue("p_vessel_draft_m", req.vesselDraftM())
                .addValue("p_arrival_date", req.arrivalDate())
                .addValue("p_departure_date", req.departureDate())
                .addValue("p_notes", req.notes());
        try {
            return jdbc.queryForObject(CREATE_SQL, params, BookingJdbcRepository::mapRow);
        } catch (DataAccessException e) {
            throw translatePostgresError(e);
        }
    }

    BookingResponse createInItinerary(CreateBookingRequest req, String customerEmail,
                                      UUID itineraryId, Integer stopOrder) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("p_berth_id", req.berthId())
                .addValue("p_customer_name", req.customerName())
                .addValue("p_customer_email", customerEmail)
                .addValue("p_vessel_name", req.vesselName())
                .addValue("p_vessel_length_m", req.vesselLengthM())
                .addValue("p_vessel_draft_m", req.vesselDraftM())
                .addValue("p_arrival_date", req.arrivalDate())
                .addValue("p_departure_date", req.departureDate())
                .addValue("p_notes", req.notes())
                .addValue("p_itinerary_id", itineraryId)
                .addValue("p_stop_order", stopOrder);
        try {
            return jdbc.queryForObject(CREATE_IN_ITINERARY_SQL, params, BookingJdbcRepository::mapRow);
        } catch (DataAccessException e) {
            throw translatePostgresError(e);
        }
    }

    int cancel(UUID id, String customerEmail) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", customerEmail);
        return jdbc.update(CANCEL_SQL, params);
    }

    private static BookingResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BookingResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("berth_id", UUID.class),
                rs.getString("customer_name"),
                rs.getString("customer_email"),
                rs.getString("vessel_name"),
                rs.getBigDecimal("vessel_length_m"),
                rs.getBigDecimal("vessel_draft_m"),
                rs.getObject("arrival_date", LocalDate.class),
                rs.getObject("departure_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_price"),
                rs.getString("notes"),
                rs.getString("confirmation_code"),
                rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toInstant()
                        : null
        );
    }

    private static RuntimeException translatePostgresError(DataAccessException dae) {
        Throwable cause = dae.getCause();
        if (cause instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null) {
                return switch (sqlState) {
                    case "P0001" -> new BookingException(BookingErrorCode.BERTH_NOT_FOUND, sqlEx.getMessage());
                    case "P0002" -> new BookingException(BookingErrorCode.VESSEL_TOO_LONG, sqlEx.getMessage());
                    case "P0003" -> new BookingException(BookingErrorCode.VESSEL_TOO_DEEP, sqlEx.getMessage());
                    case "P0004" -> new BookingException(BookingErrorCode.BERTH_UNAVAILABLE, sqlEx.getMessage());
                    default -> dae;
                };
            }
        }
        return dae;
    }
}
