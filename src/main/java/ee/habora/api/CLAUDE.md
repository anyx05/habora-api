# habora-api — Root Context

## What this is
Habora-api is the **write-side REST API** for the Habora recreational maritime
booking platform. It handles mutations: create booking, cancel booking, AI-assisted
chat (Step 4), email notifications (Step 5).

**Reads go directly from the client to Supabase via RLS — this API does not serve reads.**

## Stack
- Spring Boot 4.x · Spring Framework 7 · Jakarta EE 11 · Java 25
- Database: Supabase PostgreSQL 17.6 — existing schema, do not modify
- Auth: Supabase-issued HS256 JWTs, validated via `SUPABASE_JWT_SECRET` env var
- No JPA `@Entity` classes. All DB access via `NamedParameterJdbcTemplate`.
- Gradle Groovy DSL (`build.gradle`, not `.kts`)

## Package layout
| Package | Purpose |
|---------|---------|
| `config/` | SecurityConfig (JWT), CorsConfig, OpenApiConfig |
| `common/exception/` | ApiException, BookingException, GlobalExceptionHandler |
| `common/dto/` | ErrorResponse (shared across all domains) |
| `bookings/` | Booking write path — create via RPC, cancel via UPDATE |
| `ai/` | Chat orchestration (Anthropic SDK, filled in Step 4) |
| `notifications/` | Email on booking events (Resend, filled in Step 5) |

## Architectural rules
1. All Jakarta imports: `jakarta.*` (not `javax.*`), **except** JCA/JCE which stay in `javax.crypto.*`
2. DTOs are Java records, not Lombok-annotated classes
3. Spring beans use constructor injection — no `@Autowired` on fields
4. Never add `@Entity` classes or Flyway migrations; schema is owned by Supabase
5. `NamedParameterJdbcTemplate` is the only DB access layer
6. Package-private visibility for service/repository classes (controller is public)

## Security
- Public: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- Protected: all `/api/v1/**` requires a valid Bearer JWT
- CORS origins read from `ALLOWED_ORIGINS` env var (default: `http://localhost:3000`)

## Required env vars
| Var | Purpose |
|-----|---------|
| `SPRING_DATASOURCE_URL` | Supabase pooler JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `SUPABASE_JWT_SECRET` | JWT HMAC-SHA256 secret (omit → permissive dev decoder with WARN) |
| `ALLOWED_ORIGINS` | Comma-separated CORS origins (default: `http://localhost:3000`) |

## Run locally
```
./gradlew bootRun --args='--spring.profiles.active=local'
```
Credentials live in `src/main/resources/application-local.yml` (gitignored).
