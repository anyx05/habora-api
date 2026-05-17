# notifications — Domain Context

## Current state
Package stub — no Java files yet.

## Planned: Step 5 — Email Notifications
The `notifications` package will send transactional emails via the Resend API.

## Trigger points
| Event | Email recipient | Template |
|-------|----------------|---------|
| Booking created | `customer_email` | Booking confirmation with berth details + confirmation code |
| Booking cancelled | `customer_email` | Cancellation acknowledgement |

## Design notes
- Triggered **asynchronously** from `BookingService` after a successful create/cancel
  (Spring `@ApplicationEvent` + `@EventListener`, or `@Async`)
- Add `RESEND_API_KEY` env var
- Template engine TBD — Thymeleaf fragments preferred for HTML emails
- No retry logic at first; failed sends are logged as WARN (non-blocking)

## Files to create (Step 5)
| File | Purpose |
|------|---------|
| `NotificationService.java` | Calls Resend REST API via `RestClient` |
| `BookingNotificationListener.java` | Listens for booking events, calls NotificationService |
| `EmailTemplate.java` | Enum mapping event types to HTML template names |
