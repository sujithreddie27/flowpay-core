# FlowPay Core

Real-time payment processing platform built with Spring Boot, Kafka, PostgreSQL, and Redis.

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Client /   │────▶│   FlowPay    │────▶│  PostgreSQL  │
│   API User   │◀────│   Core API   │◀────│   (Primary)  │
└──────────────┘     └──────┬───┬───┘     └──────────────┘
                           │   │
                    ┌──────┘   └──────┐
                    ▼                 ▼
             ┌──────────┐     ┌──────────┐
             │  Apache   │     │  Redis   │
             │  Kafka    │     │  Cache   │
             └──────────┘     └──────────┘
                    │
         ┌─────────┼─────────┐
         ▼         ▼         ▼
   ┌──────────┐ ┌────────┐ ┌─────────┐
   │ Payment  │ │ Audit  │ │ Notify  │
   │ Consumer │ │Consumer│ │Consumer │
   └──────────┘ └────────┘ └─────────┘
```

**Tech Stack:** Java 17, Spring Boot 3.3, PostgreSQL 16, Apache Kafka, Redis 7, Docker, Kubernetes

## Features

- **Payment Processing** — Initiate, validate, and settle transactions with full ACID compliance
- **Idempotency** — Duplicate detection via reference ID and idempotency key
- **Validation Engine** — Pluggable validator chain (amount limits, daily limits, account status, currency, duplicates)
- **Retry & Failure Handling** — Spring Retry with exponential backoff for transient failures; dead-letter queue for permanent failures
- **Event-Driven Architecture** — Kafka topics for payment events, audit trails, and notifications
- **Caching & Rate Limiting** — Redis-backed caching with TTL policies; sliding window rate limiter
- **JWT Authentication** — Register, login, refresh tokens with BCrypt password hashing
- **Role-Based Authorization** — USER, MERCHANT, ADMIN roles with resource ownership validation
- **Webhook Delivery** — HMAC-SHA256 signed payloads with exponential backoff retry
- **Email Notifications** — Async notification delivery via Spring Mail
- **Observability** — Prometheus metrics, Grafana dashboards, structured JSON logging, distributed tracing (Zipkin/Brave)
- **Security Hardening** — XSS filter, security headers, CORS, OWASP dependency scanning
- **API Documentation** — SpringDoc OpenAPI / Swagger UI

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- (Optional) kubectl for Kubernetes deployments

## Quick Start

### 1. Clone and start infrastructure

```bash
git clone <repo-url>
cd flowpay-core
docker-compose up -d postgres redis kafka zookeeper
```

### 2. Run the application

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080` with the `dev` profile active.

### 3. Run with Docker (full stack)

```bash
docker-compose up --build
```

This starts PostgreSQL, Redis, Kafka, Zookeeper, and the FlowPay application together.

### 4. Verify

```bash
curl http://localhost:8080/actuator/health
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register a new user |
| POST | `/api/v1/auth/login` | Authenticate and get JWT |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| GET | `/api/v1/accounts` | List user accounts |
| POST | `/api/v1/accounts` | Create an account |
| GET | `/api/v1/accounts/{id}/balance` | Get account balance |
| POST | `/api/v1/payments/initiate` | Initiate a payment |
| GET | `/api/v1/payments/{id}` | Get payment status |
| POST | `/api/v1/payments/{id}/retry` | Retry a failed payment |
| GET | `/api/v1/transactions` | Transaction history (paginated, filterable) |
| GET | `/api/v1/transactions/{id}` | Transaction details |
| GET | `/api/v1/transactions/summary` | Transaction statistics |
| POST | `/api/v1/transactions/batch` | Bulk transaction processing |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

Full interactive documentation available at `/swagger-ui.html` (disabled in production).

## Project Structure

```
src/main/java/com/flowpay/
├── auth/              # User registration, login, JWT authentication
├── common/            # Shared DTOs, entities, enums, exceptions
├── config/            # App configuration (Async, JPA, Kafka, OpenAPI, Redis, Security, Tracing)
├── kafka/             # Event producers and consumers
├── logging/           # Sensitive data masking, structured logging
├── monitoring/        # Health indicators, custom metrics, alerting
├── notification/      # Email, webhook delivery, notification preferences
├── payments/          # Payment method management
├── security/          # JWT provider, filters (XSS, security headers, logging)
└── transaction/       # Core payment processing, accounts, validation engine
```

## Configuration

### Profiles

| Profile | Purpose | Activation |
|---------|---------|------------|
| `dev` | Local development with debug logging | Default (`spring.profiles.active=dev`) |
| `prod` | Production with optimized settings | `SPRING_PROFILES_ACTIVE=prod` |
| `test` | Unit tests with H2 in-memory DB | Activated in test classes |
| `integration` | Integration tests with Testcontainers | Activated in integration test classes |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `flowpay` | Database name |
| `DB_USERNAME` | `flowpay` | Database username |
| `DB_PASSWORD` | `flowpay` | Database password |
| `DB_POOL_SIZE` | `20` | HikariCP max pool size |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | `redispass` | Redis password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `JWT_SECRET` | (built-in dev key) | JWT signing secret (change in production) |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `900000` | Access token TTL in ms (15 min) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `604800000` | Refresh token TTL in ms (7 days) |
| `MAIL_HOST` | `localhost` | SMTP server host |
| `MAIL_PORT` | `1025` | SMTP server port |
| `ALERTING_ENABLED` | `false` | Enable application-level alerting |
| `ALERTING_SLACK_WEBHOOK_URL` | — | Slack webhook URL for alerts |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Distributed tracing sampling rate |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | Zipkin collector endpoint |

## Database Migrations

Flyway manages schema migrations automatically on startup. Migrations are in `src/main/resources/db/migration/`:

| Migration | Description |
|-----------|-------------|
| V1 | Users table with KYC status |
| V2 | Accounts table with optimistic locking |
| V3 | Transactions table with idempotency |
| V4 | Payment methods table |
| V5 | Audit logs table |
| V6 | Performance indexes |
| V7 | Dead letter transactions table |
| V8 | Processed events table (deduplication) |
| V9 | Notification and webhook tables |
| V10 | Performance optimization indexes |

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests (requires Docker)

```bash
mvn verify -P integration
```

Integration tests use Testcontainers to spin up PostgreSQL and Kafka automatically.

### Load Tests (Gatling)

```bash
# On Windows
.\run-load-test.ps1

# Or via Maven
mvn gatling:test
```

### Dependency Vulnerability Scan

```bash
mvn dependency-check:check
```

## Observability

### Health Checks

- `/actuator/health` — Aggregated health (DB, Redis, Kafka, payment gateway)
- `/actuator/health/liveness` — Kubernetes liveness probe
- `/actuator/health/readiness` — Kubernetes readiness probe

### Metrics

Prometheus metrics exposed at `/actuator/prometheus`. Key custom metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `payment_transactions_total` | Counter | Total transactions by status |
| `payment_processing_duration_seconds` | Histogram | Transaction processing latency |
| `active_transactions_count` | Gauge | In-flight transactions |
| `payment_failure_rate` | Rate | Transaction failure rate |
| `payment_batch_duration` | Timer | Batch processing duration |

### Grafana Dashboards

Pre-built dashboards in `src/main/resources/grafana/`:

- **Payment Dashboard** — Transaction throughput, success/failure rates, latency percentiles
- **Performance Dashboard** — JVM metrics, HikariCP pool, Kafka consumer lag
- **Alerting Dashboard** — Error rate trends, SLO compliance, alert history

### Alerting

Prometheus alerting rules defined in `k8s/prometheus-rules.yml`:

- High error rate (>5% failures)
- P99 latency >2s
- Database connection pool exhaustion (>85%)
- Kafka consumer lag
- Pod restart anomalies

Supports Slack and PagerDuty notification channels.

### Structured Logging

JSON-structured logs in production via Logstash encoder. Includes:

- Correlation IDs (`traceId`, `spanId`, `requestId`)
- Automatic sensitive data masking (passwords, tokens, card numbers)
- Request/response logging with duration tracking

## Kubernetes Deployment

Manifests in `k8s/`:

```bash
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/secret.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
kubectl apply -f k8s/hpa.yml
kubectl apply -f k8s/ingress.yml
```

### Production Defaults

- **Replicas:** 3 (auto-scales 3–10 via HPA)
- **CPU:** 500m request / 2 cores limit
- **Memory:** 512Mi request / 1Gi limit
- **HPA targets:** 70% CPU, 80% memory
- **Rolling update:** maxSurge 1, maxUnavailable 0
- **Graceful shutdown:** 60s termination grace period

## Docker

### Build

```bash
docker build -t flowpay/flowpay-core:latest .
```

Multi-stage build: Maven builder stage → JRE-only runtime image. Runs as non-root user with G1GC and container-aware memory settings.

### Production Compose

```bash
docker-compose -f docker-compose.prod.yml up -d
```

## Kafka Topics

| Topic | Description |
|-------|-------------|
| `payment-initiated` | Emitted when a payment is created |
| `payment-completed` | Emitted on successful settlement |
| `payment-failed` | Emitted on payment failure |
| `audit-events` | Audit trail events for compliance |

Producers use idempotent mode with `acks=all`. Consumers use manual acknowledgment with event deduplication.

## Security

- **Authentication:** JWT (access + refresh tokens) with BCrypt password hashing
- **Authorization:** Role-based (USER, MERCHANT, ADMIN) with `@PreAuthorize`
- **XSS Protection:** Request filter sanitizing 12 attack patterns
- **Security Headers:** X-Frame-Options, CSP, HSTS, X-Content-Type-Options, Referrer-Policy, Permissions-Policy
- **Rate Limiting:** Redis-based sliding window per user/API key
- **Distributed Locking:** Redis locks for concurrent transaction safety
- **Webhook Signatures:** HMAC-SHA256 payload verification
- **Dependency Scanning:** OWASP dependency-check plugin

## License

Proprietary — All rights reserved.