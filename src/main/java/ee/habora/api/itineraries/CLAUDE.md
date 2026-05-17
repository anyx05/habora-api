# itineraries — Domain Context

## Product concept: the multi-stop trip
An itinerary is the core differentiator of Habora vs a single-berth booking site.
A sailor planning a cruise (e.g. Tallinn → Helsinki → Stockholm) wants to plan
all stops in one session, review the full trip cost, then confirm everything
atomically so no leg is left unbooked.

This is NOT just a shopping cart. An itinerary represents a planned voyage with
an ordered sequence of port stops. `stop_order` on each booking reflects that
sequence and is surfaced to the UI as "Stop 1", "Stop 2", etc.

## Lifecycle: draft → confirmed (or cancelled)
```
draft ──── confirm_itinerary RPC ──── confirmed
  │
  └──── cancel ──── cancelled
```

- **draft**: mutable. Bookings are `pending` and have no `confirmation_code`.
  The user can add/remove stops freely. Berths are NOT held; two users can add
  the same berth to their drafts simultaneously.
- **confirmed**: immutable. `confirm_itinerary` RPC atomically confirms all
  pending bookings, generating `confirmation_code` on each and checking for
  berth conflicts that may have emerged since the stops were added. If any
  conflict is detected (P0012), the whole confirmation is aborted.
- **cancelled**: terminal. All pending bookings inside are also cancelled.

## RPC surface
| RPC | Raised errors |
|-----|---------------|
| `create_booking_safely(..., p_itinerary_id, p_stop_order)` | P0001–P0004 (berth/vessel errors) |
| `confirm_itinerary(p_itinerary_id, p_user_email)` | P0010 ITINERARY_NOT_FOUND, P0011 ITINERARY_NOT_DRAFT, P0012 BERTH_CONFLICT_IN_ITINERARY |

## Cross-package dependency
`ItineraryService` injects `BookingService` (from the `bookings` package) to
delegate booking creation. `BookingService` is marked `public` specifically to
allow this injection — it is an intentional breach of the "package-private
services" convention, documented here so future agents don't undo it.

## Authorization rules
- `user_id` is always taken from `jwt.getSubject()` (UUID) — never from the DTO.
- `customerEmail` in `AddBookingToItineraryRequest` is for client pre-fill only;
  the service overrides it with the JWT `email` claim before calling `BookingService`.
- Ownership checks: service verifies `itinerary.user_id == authenticatedUserId`
  via `findByIdWithBookings(id, userId)` before any mutation. The WHERE clause
  in the repository query is a defense-in-depth layer, not the primary check.
- Unauthenticated requests → 401 (handled by Spring Security filter chain).
- Wrong owner → 403 (intentionally does not distinguish "not found" from "not owned").
