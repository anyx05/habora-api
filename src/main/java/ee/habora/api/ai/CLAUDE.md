# ai — Domain Context

## Current state
`ChatController` is a stub returning `501 Not Implemented`.

## Planned: Step 4 — Agent Redesign
The `ai` package will own the chat orchestration loop using the Anthropic Java SDK
(`com.anthropic:anthropic-java`, already on the classpath). Target: `claude-haiku`
for low-latency, multi-turn booking conversations.

## Hybrid agent design (planned)
- **Open-ended questions** → free-form text reply
- **Structured booking flow** → emit UI directives (JSON with an `action` field)
  so the frontend can render form steps without natural-language parsing

## Key files to create (Step 4)
| File | Purpose |
|------|---------|
| `ChatService.java` | Orchestrates Anthropic SDK calls, manages tool-use loop |
| `ChatHistoryRepository.java` | Reads/writes `chat_history` table via JdbcTemplate |
| `AgentSettingsRepository.java` | Reads `agent_settings` (system prompt, mode, language) |
| `dto/ChatRequest.java` | `{ sessionId, portId, message }` |
| `dto/ChatResponse.java` | `{ sessionId, reply, directives? }` |

## Tool definitions (planned)
Tools the agent can invoke, mapped to existing Postgres functions:
- `find_ports_near(lat, lng, radius_km, vessel_length, vessel_draft)`
- `check_berth_availability(port_id, arrival, departure, vessel_length, vessel_draft)`
- `create_booking_safely(...)` — called via `BookingService`, not direct SQL

## Session design
`session_id` is a UUID generated client-side. History is persisted in `chat_history`
so conversations survive page refreshes. `port_id` scopes the agent to a specific port's
`agent_settings` (system prompt, language hint).
