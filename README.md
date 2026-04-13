# Spring AI Hotel Booking Agent

This is a Java Spring Boot implementation of the enterprise two-step booking flow with pluggable preview providers:
- `mock`
- `rapidapi`

## What it enforces

- Mandatory preview-first flow (`hotelPreviewTool` before final handoff)
- Explicit confirmation gate (`Do you confirm this booking at [Price]?`)
- PII hard-stop for card/CVV/government ID data
- Relative-date rejection unless exact `YYYY-MM-DD` is provided
- JSON-only `READY_FOR_CREATE` payload on confirmation

## API

`POST /api/booking/turn`

Request:

```json
{
  "sessionId": "user-123",
  "userMessage": "hotelId 6745031 checkin 2026-05-10 checkout 2026-05-15",
  "hotelId": "6745031",
  "checkin": "2026-05-10",
  "checkout": "2026-05-15",
  "adultCount": 2
}
```

Response:

```json
{
  "state": "WAITING_FOR_CONFIRMATION",
  "reply": "Preview: Total price is EUR 542.00. Cancellation policy: Free cancellation until 48 hours before check-in. Do you confirm this booking at EUR 542.00?"
}
```

If user confirms, `reply` is the exact `READY_FOR_CREATE` JSON block.

## Environment variables

Choose provider:

```bash
export PREVIEW_PROVIDER="rapidapi"
```

For `rapidapi` mode:

```bash
export RAPIDAPI_KEY="your_rapidapi_key"
export RAPIDAPI_HOST="booking-com.p.rapidapi.com"
export RAPIDAPI_BASE_URL="https://booking-com.p.rapidapi.com"
```

Optional overrides:

```bash
export RAPIDAPI_LOCALE="en-gb"
export RAPIDAPI_CURRENCY="EUR"
```

## Run

```bash
mvn spring-boot:run
```

## Local Redis

```bash
docker compose up -d redis
```

## Agent Dependency Health

- `GET /api/booking/health/agents` - configuration-level checks
- `GET /api/booking/health/agents?deep=true` - live outbound checks to Redis/OpenAI/RapidAPI

## Provider behavior

- `rapidapi`: calls `GET /v1/hotels/search` on the configured RapidAPI host, parses price/cancellation, then asks confirmation.
- `mock`: local deterministic response for development.

For RapidAPI, request headers are:
- `x-rapidapi-key: <key>`
- `x-rapidapi-host: <host>`

Docs:
- [RapidAPI Hub](https://rapidapi.com)
