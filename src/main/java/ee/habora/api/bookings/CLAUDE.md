# bookings — Domain Context

## Write path overview
All booking mutations go through Postgres functions or tightly-scoped UPDATEs.
No raw INSERT in Java.

## Create booking
Calls `create_booking_safely(p_berth_id, p_customer_name, p_customer_email,
p_vessel_name, p_vessel_length_m, p_vessel_draft_m, p_arrival_date,
p_departure_date, p_notes)`.

The function raises `RAISE EXCEPTION` with application-level SQLState codes:

| SQLState | BookingErrorCode | HTTP |
|----------|-----------------|------|
| P0001 | BERTH_NOT_FOUND | 404 |
| P0002 | VESSEL_TOO_LONG | 422 |
| P0003 | VESSEL_TOO_DEEP | 422 |
| P0004 | BERTH_UNAVAILABLE | 409 |

Mapping lives in `BookingJdbcRepository.translatePostgresError()`.
`BookingException` is caught and mapped to HTTP status by `GlobalExceptionHandler`.

## Cancel booking
Direct UPDATE:
```sql
UPDATE bookings SET status = 'cancelled'
WHERE id = :id AND customer_email = :email AND status != 'cancelled'
```
Returns 0 rows → 404 (intentionally ambiguous — don't reveal whether booking exists).

## Authorization rules
- `customerEmail` in `CreateBookingRequest` is for display/pre-fill only.
- `BookingService` **always overrides it with the JWT `email` claim** to prevent spoofing.
- Cancel ownership is enforced at the DB level via `WHERE customer_email = :email`.

## Berths table note
The `berths` table has legacy columns (`length`, `draft`, `price`) — ignore them.
Always use: `max_length_m`, `max_draft_m`, `max_beam_m`, `price_per_night`, `amenities[]`, `status`, `is_active`.
