package ee.habora.api.itineraries;

import ee.habora.api.bookings.dto.BookingResponse;
import ee.habora.api.common.exception.BookingException;
import ee.habora.api.common.exception.BookingException.BookingErrorCode;
import ee.habora.api.itineraries.dto.ConfirmedBookingResponse;
import ee.habora.api.itineraries.dto.ItineraryResponse;
import ee.habora.api.itineraries.dto.ItineraryWithBookingsResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ItineraryJdbcRepository {

    private static final String INSERT_SQL = """
            INSERT INTO itineraries (user_id, name, notes, status)
            VALUES (:userId, :name, :notes, 'draft')
            RETURNING id, user_id, name, status, notes, created_at, updated_at
            """;

    private static final String FIND_CURRENT_DRAFT_SQL = """
            WITH current_draft AS (
                SELECT id FROM itineraries
                WHERE user_id = :userId AND status = 'draft'
                ORDER BY created_at DESC
                LIMIT 1
            )
            SELECT i.id          AS itin_id,
                   i.user_id,
                   i.name        AS itin_name,
                   i.status      AS itin_status,
                   i.notes       AS itin_notes,
                   i.created_at  AS itin_created,
                   i.updated_at  AS itin_updated,
                   b.id          AS b_id,
                   b.berth_id,
                   b.customer_name,
                   b.customer_email,
                   b.vessel_name,
                   b.vessel_length_m,
                   b.vessel_draft_m,
                   b.arrival_date,
                   b.departure_date,
                   b.status      AS b_status,
                   b.total_price,
                   b.notes       AS b_notes,
                   NULL::text    AS confirmation_code,
                   b.created_at  AS b_created
            FROM itineraries i
            JOIN current_draft cd ON cd.id = i.id
            LEFT JOIN bookings b ON b.itinerary_id = i.id
            ORDER BY b.stop_order ASC NULLS LAST
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT i.id          AS itin_id,
                   i.user_id,
                   i.name        AS itin_name,
                   i.status      AS itin_status,
                   i.notes       AS itin_notes,
                   i.created_at  AS itin_created,
                   i.updated_at  AS itin_updated,
                   b.id          AS b_id,
                   b.berth_id,
                   b.customer_name,
                   b.customer_email,
                   b.vessel_name,
                   b.vessel_length_m,
                   b.vessel_draft_m,
                   b.arrival_date,
                   b.departure_date,
                   b.status      AS b_status,
                   b.total_price,
                   b.notes       AS b_notes,
                   NULL::text    AS confirmation_code,
                   b.created_at  AS b_created
            FROM itineraries i
            LEFT JOIN bookings b ON b.itinerary_id = i.id
            WHERE i.id = :id AND i.user_id = :userId
            ORDER BY b.stop_order ASC NULLS LAST
            """;

    private static final String CONFIRM_SQL = """
            SELECT * FROM confirm_itinerary(:p_itinerary_id, :p_user_email)
            """;

    private static final String CANCEL_BOOKINGS_SQL = """
            UPDATE bookings
               SET status = 'cancelled'
             WHERE itinerary_id = :itineraryId AND status = 'pending'
            """;

    private static final String CANCEL_ITINERARY_SQL = """
            UPDATE itineraries
               SET status = 'cancelled', updated_at = now()
             WHERE id = :id AND user_id = :userId AND status = 'draft'
            """;

    private static final String DELETE_BOOKING_SQL = """
            DELETE FROM bookings
             WHERE id = :bookingId AND itinerary_id = :itineraryId AND status = 'pending'
            """;

    private static final String MAX_STOP_ORDER_SQL = """
            SELECT COALESCE(MAX(stop_order), 0) FROM bookings WHERE itinerary_id = :itineraryId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ItineraryJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    ItineraryResponse insertItinerary(UUID userId, String name, String notes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("name", name)
                .addValue("notes", notes);
        return jdbc.queryForObject(INSERT_SQL, params, ItineraryJdbcRepository::mapItineraryRow);
    }

    Optional<ItineraryWithBookingsResponse> findCurrentDraftWithBookings(UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId);
        return Optional.ofNullable(
                jdbc.query(FIND_CURRENT_DRAFT_SQL, params, ItineraryJdbcRepository::extractItinerary));
    }

    Optional<ItineraryWithBookingsResponse> findByIdWithBookings(UUID id, UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId);
        return Optional.ofNullable(
                jdbc.query(FIND_BY_ID_SQL, params, ItineraryJdbcRepository::extractItinerary));
    }

    List<ConfirmedBookingResponse> confirmItinerary(UUID itineraryId, String userEmail) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("p_itinerary_id", itineraryId)
                .addValue("p_user_email", userEmail);
        try {
            return jdbc.query(CONFIRM_SQL, params, ItineraryJdbcRepository::mapConfirmedBookingRow);
        } catch (DataAccessException e) {
            throw translatePostgresError(e);
        }
    }

    int cancelItinerary(UUID id, UUID userId) {
        MapSqlParameterSource bookingParams = new MapSqlParameterSource()
                .addValue("itineraryId", id);
        jdbc.update(CANCEL_BOOKINGS_SQL, bookingParams);

        MapSqlParameterSource itinParams = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId);
        return jdbc.update(CANCEL_ITINERARY_SQL, itinParams);
    }

    int deleteBookingFromItinerary(UUID itineraryId, UUID bookingId, UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookingId", bookingId)
                .addValue("itineraryId", itineraryId);
        return jdbc.update(DELETE_BOOKING_SQL, params);
    }

    int findMaxStopOrder(UUID itineraryId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("itineraryId", itineraryId);
        Integer max = jdbc.queryForObject(MAX_STOP_ORDER_SQL, params, Integer.class);
        return max != null ? max : 0;
    }

    private static ItineraryResponse mapItineraryRow(ResultSet rs, int rowNum) throws SQLException {
        return new ItineraryResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("name"),
                rs.getString("status"),
                rs.getString("notes"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
        );
    }

    private static ItineraryWithBookingsResponse extractItinerary(ResultSet rs) throws SQLException {
        UUID itinId = null;
        UUID userId = null;
        String name = null;
        String status = null;
        String notes = null;
        java.time.Instant createdAt = null;
        java.time.Instant updatedAt = null;
        List<BookingResponse> bookings = new ArrayList<>();

        while (rs.next()) {
            if (itinId == null) {
                itinId = rs.getObject("itin_id", UUID.class);
                userId = rs.getObject("user_id", UUID.class);
                name = rs.getString("itin_name");
                status = rs.getString("itin_status");
                notes = rs.getString("itin_notes");
                createdAt = rs.getTimestamp("itin_created") != null
                        ? rs.getTimestamp("itin_created").toInstant() : null;
                updatedAt = rs.getTimestamp("itin_updated") != null
                        ? rs.getTimestamp("itin_updated").toInstant() : null;
            }
            if (rs.getObject("b_id") != null) {
                bookings.add(mapBookingRow(rs));
            }
        }

        if (itinId == null) {
            return null;
        }

        BigDecimal total = bookings.stream()
                .map(BookingResponse::totalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ItineraryWithBookingsResponse(itinId, userId, name, status, notes,
                createdAt, updatedAt, bookings, total);
    }

    private static BookingResponse mapBookingRow(ResultSet rs) throws SQLException {
        return new BookingResponse(
                rs.getObject("b_id", UUID.class),
                rs.getObject("berth_id", UUID.class),
                rs.getString("customer_name"),
                rs.getString("customer_email"),
                rs.getString("vessel_name"),
                rs.getBigDecimal("vessel_length_m"),
                rs.getBigDecimal("vessel_draft_m"),
                rs.getObject("arrival_date", LocalDate.class),
                rs.getObject("departure_date", LocalDate.class),
                rs.getString("b_status"),
                rs.getBigDecimal("total_price"),
                rs.getString("b_notes"),
                rs.getString("confirmation_code"),
                rs.getTimestamp("b_created") != null ? rs.getTimestamp("b_created").toInstant() : null
        );
    }

    private static ConfirmedBookingResponse mapConfirmedBookingRow(ResultSet rs, int rowNum) throws SQLException {
        return new ConfirmedBookingResponse(
                rs.getObject("booking_id", UUID.class),
                rs.getString("confirmation_code"),
                rs.getObject("berth_id", UUID.class),
                rs.getObject("arrival_date", LocalDate.class),
                rs.getObject("departure_date", LocalDate.class),
                rs.getBigDecimal("total_price")
        );
    }

    private static RuntimeException translatePostgresError(DataAccessException dae) {
        Throwable cause = dae.getCause();
        if (cause instanceof java.sql.SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null) {
                return switch (sqlState) {
                    case "P0010" -> new BookingException(BookingErrorCode.ITINERARY_NOT_FOUND, sqlEx.getMessage());
                    case "P0011" -> new BookingException(BookingErrorCode.ITINERARY_NOT_DRAFT, sqlEx.getMessage());
                    case "P0012" -> new BookingException(BookingErrorCode.BERTH_CONFLICT_IN_ITINERARY, sqlEx.getMessage());
                    default -> dae;
                };
            }
        }
        return dae;
    }
}
