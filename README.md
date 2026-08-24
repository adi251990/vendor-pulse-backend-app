# Vendor Pulse Backend App

This repository contains the backend service for Vendor Pulse, a hiring platform.

## Quick start

- Build with Maven:

```bash
mvn -B -DskipTests package
```

- Run (after build):

```bash
java -jar target/*.jar
```

## CI

This repository uses GitHub Actions to build and test on push/pull requests to `main`.

## Contact

Maintainer: adi251990 (adi251990@gmail.com)
# HireMe Backend

Java 25 / Spring Boot 3.3 backend for the HireMe hourly staffing platform
(tri-party model: **OWNER**=Vendor, **STAFF**=Worker, **ADMIN**=Platform
Operator). Built as a REST API for an Android client. This implements the
system spec discussed earlier in this conversation — see that document for
the full design rationale; this README covers only how to run what's here.

## Stack

Spring Boot 3.3 · PostgreSQL 15 + PostGIS · Flyway · Redisson (distributed
locks) · Spring Kafka · Spring Security (JWT) · Stripe Connect · Twilio ·
springdoc-openapi.

## Project layout

```
src/main/java/com/hireme/platform/
  identity/     users, vendors, staff profiles, JWT auth, RBAC
  shift/        shift CRUD + dynamic pricing engine
  booking/      claim (Redisson-locked) + matching/scoring
  attendance/   geofenced clock-in/out, device signature, geofence math
  timesheet/    approval / dispute state machine
  payment/      Stripe Connect charges, escrow, dispute resolution
  notification/ Twilio SMS + push (stub) + cascade escalation
  scheduler/    no-show sweep, notification cascade, auto-approve, disputes
  event/        Kafka event publisher (shift/booking/timesheet/payment/noshow topics)
  config/       security, JWT, Redisson, Kafka, pricing/matching/geofence properties
  common/       shared exceptions, geo/holiday utilities
src/main/resources/db/migration/   Flyway SQL (schema + refresh_tokens)
docker-compose.yml                 Postgres+PostGIS, Redis, Kafka, Zookeeper
```

## Running locally

1. **Start infra:**
   ```
   docker compose up -d
   ```
2. **Set secrets** (or just use the dev defaults in `application.yml` for a
   first smoke test — they are NOT safe for anything beyond local dev):
   ```
   export JWT_SECRET=... 
   export STRIPE_SECRET_KEY=sk_test_...
   export STRIPE_WEBHOOK_SECRET=whsec_...
   export TWILIO_ACCOUNT_SID=...
   export TWILIO_AUTH_TOKEN=...
   export TWILIO_FROM_NUMBER=+1...
   ```
3. **Run:**
   ```
   mvn spring-boot:run
   ```
   Flyway migrates the schema automatically on boot. Swagger UI is at
   `http://localhost:8080/docs`.

## Smoke-testing the core flows

```
# 1. Register an Owner (creates a Vendor org) and a Staff account
curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{
  "email":"owner@acme.com","phone":"+15550000001","password":"password123","role":"OWNER","orgLegalName":"Acme Staffing"}'

curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{
  "email":"staff@acme.com","phone":"+15550000002","password":"password123","role":"STAFF"}'

# 2. Attach a Stripe customer id to the org (normally comes from a Stripe Setup Intent client-side)
curl -X PATCH localhost:8080/api/v1/vendors/me/payment-method -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' -d '{"stripeCustomerId":"cus_test123"}'

# 3. Create + publish a shift, then claim it as Staff
curl -X POST localhost:8080/api/v1/shifts -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' -d '{
  "title":"Warehouse Loader","hourlyRate":24.50,"headcount":1,
  "siteCenter":{"lat":34.0522,"lon":-118.2437},"geofenceRadiusM":150,
  "startTime":"2026-09-01T22:00:00Z","endTime":"2026-09-02T06:00:00Z"}'

curl -X POST localhost:8080/api/v1/shifts/{shiftId}/publish -H "Authorization: Bearer $OWNER_TOKEN"
curl -X POST localhost:8080/api/v1/bookings/{shiftId}/claim -H "Authorization: Bearer $STAFF_TOKEN"
```

## Known scope-limiting simplifications (read before production use)

These were called out inline in code comments too — flagging them here so
they're not missed:

- **Weekly overtime** is evaluated per-shift (daily threshold only), not
  aggregated across a worker's shifts platform-wide across the ISO week, as
  the full spec's FLSA-aware pricing engine calls for. Needs a query summing
  hours per staff member across all timesheets in the current week.
- **Geofence GPS-drift** uses a single-reading tolerance check; the spec's
  "two consecutive readings" anti-spoofing rule needs short-lived
  request-to-request state (e.g. a Redis key) that isn't wired up yet.
- **Device attestation** checks token presence only — swap in a real Play
  Integrity API verdict call in `ClockService`/`DeviceSignatureVerifier`.
- **Device signing keys** use one shared platform secret; move to
  per-device keys issued at enrollment before shipping.
- **Dispute hour adjustments** update the timesheet's hours but don't yet
  re-run `PricingEngineService` to recompute `basePay`/`markupFee`/
  `billRate` before re-charging — see the TODO in `DisputeService.resolve`.
- **Checkr/Stripe webhooks** are unauthenticated/minimally verified stubs
  (Stripe's is signature-verified; Checkr's is not) — harden before go-live.
- Redis geo hot-path (GEOSEARCH) described in the spec for the 9AM
  concurrency spike isn't wired in; `MatchingService` currently queries
  Postgres with a bounding-box pre-filter + in-JVM Haversine scoring, which
  is the spec's "cold-start/reconciliation" path used as the only path here.

## Build verification note

This project could not be compiled with Maven in the environment it was
generated in (no Maven Central network access there). It was written and
manually cross-checked file-by-file for signature/type consistency, but run
`mvn clean verify` yourself before deploying — that's a normal step for any
generated codebase, not a substitute for it.
