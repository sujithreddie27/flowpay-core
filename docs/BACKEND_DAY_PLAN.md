# FlowPay Core - Backend Day-Wise Development Plan

> **Project:** Real-Time Payment Processing Platform  
> **Tech Stack:** Java, Spring Boot, Kafka, PostgreSQL, Redis, Docker, Kubernetes, AWS  
> **Duration:** 5 Weeks (24 Working Days)  
> **Repo:** flowpay-core (Backend)

---

## Week 1: Project Setup, Core Models & Database

### Day 1 — Project Initialization & Maven Setup
- [ ] Configure `pom.xml` with Spring Boot parent, Java 17+
- [ ] Add dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway, Lombok, MapStruct
- [ ] Add dependencies: Spring Boot Starter Validation, Spring Boot Starter Actuator
- [ ] Create main application class `FlowPayApplication.java`
- [ ] Configure `application.yml` (server port, datasource, JPA, Flyway)
- [ ] Set up multi-environment profiles (`application-dev.yml`, `application-prod.yml`)
- [ ] Verify application starts successfully with `mvn spring-boot:run`

### Day 2 — Database Design & Migrations
- [ ] Design ER diagram for: `users`, `accounts`, `transactions`, `payment_methods`, `audit_logs`
- [ ] Create Flyway migration `V1__create_users_table.sql`
- [ ] Create Flyway migration `V2__create_accounts_table.sql`
- [ ] Create Flyway migration `V3__create_transactions_table.sql`
- [ ] Create Flyway migration `V4__create_payment_methods_table.sql`
- [ ] Create Flyway migration `V5__create_audit_logs_table.sql`
- [ ] Add indexes on frequently queried columns (status, user_id, created_at)
- [ ] Run migrations and verify schema in PostgreSQL

### Day 3 — JPA Entities & Repositories
- [ ] Create `BaseEntity` with common fields (id, createdAt, updatedAt)
- [ ] Create entity: `User` (id, email, name, phone, status, kycStatus)
- [ ] Create entity: `Account` (id, userId, balance, currency, accountType, status)
- [ ] Create entity: `Transaction` (id, senderId, receiverId, amount, currency, status, type, referenceId)
- [ ] Create entity: `PaymentMethod` (id, userId, type, provider, tokenizedDetails, isDefault)
- [ ] Create entity: `AuditLog` (id, entityType, entityId, action, oldValue, newValue, performedBy)
- [ ] Create JPA repositories for all entities
- [ ] Add custom query methods (findByStatus, findByUserId, etc.)

### Day 4 — DTOs, Mappers & Common Utilities
- [ ] Create request/response DTOs for each entity
- [ ] Set up MapStruct mappers (Entity ↔ DTO conversion)
- [ ] Create common package: `ApiResponse<T>` wrapper
- [ ] Create common package: `PagedResponse<T>` for pagination
- [ ] Create custom exceptions: `PaymentException`, `InsufficientFundsException`, `TransactionNotFoundException`
- [ ] Create global exception handler (`@ControllerAdvice`)
- [ ] Create enums: `TransactionStatus`, `TransactionType`, `PaymentMethodType`, `AccountStatus`

### Day 5 — Docker Setup & Local Dev Environment
- [ ] Create `Dockerfile` (multi-stage build)
- [ ] Create `docker-compose.yml` (app, PostgreSQL, Redis, Kafka + Zookeeper)
- [ ] Add health check endpoints
- [ ] Configure `.env` file for local environment variables
- [ ] Test full stack locally with `docker-compose up`
- [ ] Document local setup steps in `README.md`

---

## Week 2: Core Payment Processing Logic

### Day 6 — Account Service & Balance Management
- [ ] Create `AccountService` interface and implementation
- [ ] Implement: createAccount, getAccountById, getAccountsByUserId
- [ ] Implement: creditAccount, debitAccount (with optimistic locking)
- [ ] Add `@Version` field for optimistic concurrency control
- [ ] Implement balance validation (prevent negative balances)
- [ ] Create `AccountController` with REST endpoints
- [ ] Write unit tests for AccountService

### Day 7 — Payment Validation Engine
- [ ] Create `PaymentValidationService`
- [ ] Implement validation rules:
  - Minimum/maximum transaction amount
  - Daily transaction limit per user
  - Account status check (active/frozen/closed)
  - Currency mismatch validation
  - Duplicate transaction detection (idempotency key)
- [ ] Create `ValidationResult` class with error details
- [ ] Chain validators using Strategy pattern
- [ ] Write unit tests for all validation rules

### Day 8 — Transaction Processing Service
- [ ] Create `TransactionService` interface and implementation
- [ ] Implement `initiatePayment()` flow:
  1. Validate payment request
  2. Check sender balance
  3. Create transaction record (PENDING status)
  4. Debit sender account
  5. Credit receiver account
  6. Update transaction status (COMPLETED/FAILED)
- [ ] Use `@Transactional` with proper isolation level
- [ ] Implement idempotency using referenceId/idempotencyKey
- [ ] Create `TransactionController` with REST endpoints
- [ ] Write unit tests for transaction processing

### Day 9 — Retry & Failure Handling
- [ ] Add Spring Retry dependency
- [ ] Implement retry logic for transient failures (`@Retryable`)
- [ ] Create `FailedTransactionHandler` for permanent failures
- [ ] Implement transaction rollback on partial failures
- [ ] Create `TransactionStatus` state machine (PENDING → PROCESSING → COMPLETED/FAILED/REVERSED)
- [ ] Implement manual retry endpoint for failed transactions
- [ ] Add dead letter handling for unrecoverable transactions
- [ ] Write integration tests for failure scenarios

### Day 10 — Transaction History & Querying
- [ ] Implement paginated transaction history endpoint
- [ ] Add filters: by status, type, date range, amount range
- [ ] Implement `Specification<Transaction>` for dynamic queries
- [ ] Create transaction summary/statistics endpoint (daily/weekly/monthly totals)
- [ ] Add sorting support (by date, amount, status)
- [ ] Implement transaction receipt/details endpoint
- [ ] Write integration tests

---

## Week 3: Kafka Integration, Caching & Security

### Day 11 — Kafka Producer Setup
- [ ] Add Spring Kafka dependency
- [ ] Configure Kafka properties in `application.yml`
- [ ] Create Kafka topics: `payment-initiated`, `payment-completed`, `payment-failed`, `audit-events`
- [ ] Create `PaymentEvent` DTO (eventId, transactionId, type, timestamp, payload)
- [ ] Implement `PaymentEventProducer` service
- [ ] Publish events at each transaction state change
- [ ] Add serialization config (JSON serializer with schema)
- [ ] Test event publishing locally

### Day 12 — Kafka Consumer & Event Processing
- [ ] Create `PaymentEventConsumer` for `payment-completed` events
- [ ] Create `NotificationEventConsumer` for triggering notifications
- [ ] Create `AuditEventConsumer` for writing audit logs
- [ ] Implement consumer error handling (retry + DLT)
- [ ] Configure consumer groups and partition assignment
- [ ] Add `@KafkaListener` with proper acknowledgment mode
- [ ] Implement event deduplication (prevent double processing)
- [ ] Write integration tests with Embedded Kafka

### Day 13 — Redis Caching Layer
- [ ] Add Spring Data Redis dependency
- [ ] Configure Redis connection in `application.yml`
- [ ] Implement caching for:
  - Account balance lookups (`@Cacheable`)
  - User profile data
  - Transaction status checks
- [ ] Set TTL policies for each cache type
- [ ] Implement cache eviction on data updates (`@CacheEvict`)
- [ ] Create `RateLimiter` using Redis (sliding window)
- [ ] Implement distributed locking for concurrent transactions (Redisson)
- [ ] Write tests verifying cache behavior

### Day 14 — Authentication & JWT Security
- [ ] Add Spring Security + JWT dependencies (jjwt library)
- [ ] Create `JwtTokenProvider` (generate, validate, extract claims)
- [ ] Create `JwtAuthenticationFilter` (OncePerRequestFilter)
- [ ] Configure `SecurityConfig` (endpoint protection rules)
- [ ] Create `AuthController`: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`
- [ ] Implement `UserDetailsService` with database lookup
- [ ] Password encoding with BCrypt
- [ ] Write security tests (unauthorized access, expired tokens)

### Day 15 — Authorization & API Security
- [ ] Implement role-based access (USER, MERCHANT, ADMIN)
- [ ] Add `@PreAuthorize` annotations on controllers
- [ ] Implement resource ownership validation (users can only access own data)
- [ ] Add rate limiting per API key / user (using Redis)
- [ ] Configure CORS policy
- [ ] Add request/response logging filter (mask sensitive data)
- [ ] Implement API versioning (`/api/v1/...`)
- [ ] Write authorization integration tests

---

## Week 4: Monitoring, Testing & Advanced Features

### Day 16 — Actuator & Health Checks
- [ ] Configure Spring Boot Actuator endpoints
- [ ] Create custom health indicators:
  - Database connectivity
  - Kafka broker connectivity
  - Redis connectivity
  - External payment gateway health
- [ ] Expose `/actuator/health`, `/actuator/info`, `/actuator/metrics`
- [ ] Configure info endpoint with build info (git commit, version)
- [ ] Secure actuator endpoints (admin only)

### Day 17 — Metrics & Prometheus Integration
- [ ] Add Micrometer + Prometheus dependency
- [ ] Create custom metrics:
  - `payment_transactions_total` (counter by status)
  - `payment_processing_duration_seconds` (histogram)
  - `active_transactions_count` (gauge)
  - `payment_failure_rate` (rate)
  - `account_balance_total` (gauge)
- [ ] Add `@Timed` annotations on service methods
- [ ] Expose `/actuator/prometheus` endpoint
- [ ] Create Grafana dashboard JSON (transaction throughput, latency, error rates)

### Day 18 — Structured Logging & Distributed Tracing
- [ ] Configure Logback with JSON structured logging
- [ ] Add correlation ID (traceId) to all requests via MDC filter
- [ ] Implement request/response logging interceptor
- [ ] Add Sleuth/Micrometer Tracing for distributed tracing
- [ ] Configure log levels per package
- [ ] Create `AuditLogService` — write critical actions to audit_logs table
- [ ] Mask sensitive data in logs (card numbers, passwords)
- [ ] Test trace propagation through Kafka events

### Day 19 — Comprehensive Unit & Integration Tests
- [ ] Set up test configuration (`application-test.yml`, Testcontainers)
- [ ] Write unit tests for all service classes (mock dependencies)
- [ ] Write integration tests for:
  - Full payment flow (initiate → process → complete)
  - Concurrent transaction handling
  - Idempotency verification
  - Retry behavior on failure
- [ ] Write repository tests with `@DataJpaTest`
- [ ] Write controller tests with `@WebMvcTest`
- [ ] Aim for 80%+ code coverage
- [ ] Add test for Kafka event flow with EmbeddedKafka

### Day 20 — Notification Service & Webhooks
- [ ] Create `NotificationService` interface
- [ ] Implement email notification (Spring Mail or SES)
- [ ] Implement webhook delivery for merchants (POST callbacks)
- [ ] Create `WebhookConfig` entity (url, events, secret, status)
- [ ] Implement webhook signature verification (HMAC-SHA256)
- [ ] Add webhook retry with exponential backoff
- [ ] Create notification preferences per user
- [ ] Write tests for notification delivery

---

## Week 5: DevOps, Performance & Production Readiness

### Day 21 — Kubernetes Manifests
- [ ] Create `k8s/deployment.yml` (replicas, resource limits, probes)
- [ ] Create `k8s/service.yml` (ClusterIP, LoadBalancer)
- [ ] Create `k8s/configmap.yml` (environment-specific config)
- [ ] Create `k8s/secret.yml` (DB credentials, JWT secret, API keys)
- [ ] Create `k8s/hpa.yml` (Horizontal Pod Autoscaler based on CPU/memory)
- [ ] Create `k8s/ingress.yml` (path-based routing, TLS)
- [ ] Add liveness and readiness probes
- [ ] Test deployment locally with Minikube/Kind

### Day 22 — Performance Optimization & Load Testing
- [ ] Implement database connection pooling (HikariCP tuning)
- [ ] Add database query optimization (explain analyze slow queries)
- [ ] Implement batch processing for bulk transactions
- [ ] Configure async processing with `@Async` and thread pool
- [ ] Set up Gatling/JMeter load test scripts
- [ ] Run load tests: target 1000+ TPS
- [ ] Identify and fix bottlenecks
- [ ] Optimize Kafka consumer parallelism

### Day 23 — API Documentation & Final Security Hardening
- [ ] Add SpringDoc OpenAPI (Swagger UI) dependency
- [ ] Annotate all controllers with `@Operation`, `@ApiResponse`
- [ ] Generate API documentation at `/swagger-ui.html`
- [ ] Security hardening checklist:
  - Input sanitization (prevent SQL injection, XSS)
  - Secure headers (Content-Security-Policy, X-Frame-Options)
  - HTTPS enforcement
  - Secret rotation strategy
  - Dependency vulnerability scan (`mvn dependency-check:check`)
- [ ] Create Postman collection for all endpoints
- [ ] Export OpenAPI spec JSON/YAML

### Day 24 — Final Testing, Documentation & Cleanup
- [ ] Final integration test on all modules
- [ ] Verify health checks and monitoring dashboards
- [ ] Set up alerting rules (PagerDuty/Slack):
  - High error rate (>5%)
  - Transaction processing latency (>2s p99)
  - Pod restart alerts
  - Database connection pool exhaustion
- [ ] Update `README.md` with:
  - Architecture diagram
  - API overview
  - Setup instructions
  - Environment variables reference
- [ ] Create `CONTRIBUTING.md` and `CHANGELOG.md`
- [ ] Final code review and cleanup

---

## Project Structure (Target)

```
flowpay-core/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
├── CONTRIBUTING.md
├── docker/
│   └── init-scripts/
├── k8s/
│   ├── deployment.yml
│   ├── service.yml
│   ├── configmap.yml
│   ├── secret.yml
│   ├── hpa.yml
│   └── ingress.yml
├── docs/
│   ├── architecture.md
│   ├── api-spec.yml
│   └── er-diagram.png
├── src/
│   ├── main/
│   │   ├── java/com/flowpay/
│   │   │   ├── FlowPayApplication.java
│   │   │   ├── auth/
│   │   │   │   ├── controller/AuthController.java
│   │   │   │   ├── dto/ (LoginRequest, RegisterRequest, AuthResponse)
│   │   │   │   └── service/AuthService.java
│   │   │   ├── common/
│   │   │   │   ├── dto/ (ApiResponse, PagedResponse)
│   │   │   │   ├── entity/BaseEntity.java
│   │   │   │   ├── enums/ (TransactionStatus, TransactionType, etc.)
│   │   │   │   ├── exception/ (GlobalExceptionHandler, custom exceptions)
│   │   │   │   └── util/ (DateUtils, MaskingUtils)
│   │   │   ├── config/
│   │   │   │   ├── KafkaConfig.java
│   │   │   │   ├── RedisConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── AsyncConfig.java
│   │   │   ├── kafka/
│   │   │   │   ├── producer/PaymentEventProducer.java
│   │   │   │   ├── consumer/PaymentEventConsumer.java
│   │   │   │   └── dto/PaymentEvent.java
│   │   │   ├── monitoring/
│   │   │   │   ├── health/ (custom health indicators)
│   │   │   │   └── metrics/ (custom metrics)
│   │   │   ├── payments/
│   │   │   │   ├── controller/PaymentController.java
│   │   │   │   ├── dto/ (PaymentRequest, PaymentResponse)
│   │   │   │   ├── service/ (PaymentService, PaymentValidationService)
│   │   │   │   └── repository/PaymentMethodRepository.java
│   │   │   ├── security/
│   │   │   │   ├── jwt/ (JwtTokenProvider, JwtAuthFilter)
│   │   │   │   └── service/UserDetailsServiceImpl.java
│   │   │   └── transaction/
│   │   │       ├── controller/TransactionController.java
│   │   │       ├── dto/ (TransactionRequest, TransactionResponse)
│   │   │       ├── entity/ (Transaction, Account)
│   │   │       ├── repository/ (TransactionRepository, AccountRepository)
│   │   │       └── service/ (TransactionService, AccountService)
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── application-test.yml
│   │       ├── logback-spring.xml
│   │       └── db/migration/ (V1 through V5 SQL files)
│   └── test/
│       └── java/com/flowpay/
│           ├── transaction/service/TransactionServiceTest.java
│           ├── payments/service/PaymentValidationServiceTest.java
│           └── integration/ (full flow integration tests)
└── gatling/ (load test scripts)
```

---

## Key API Endpoints (Target)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register new user |
| POST | `/api/v1/auth/login` | Login & get JWT |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| GET | `/api/v1/accounts` | Get user accounts |
| POST | `/api/v1/accounts` | Create account |
| GET | `/api/v1/accounts/{id}/balance` | Get account balance |
| POST | `/api/v1/payments/initiate` | Initiate payment |
| GET | `/api/v1/payments/{id}` | Get payment status |
| POST | `/api/v1/payments/{id}/retry` | Retry failed payment |
| GET | `/api/v1/transactions` | Transaction history (paginated) |
| GET | `/api/v1/transactions/{id}` | Transaction details |
| GET | `/api/v1/transactions/summary` | Transaction statistics |
| GET | `/api/v1/admin/dashboard` | Admin monitoring dashboard data |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

---

## Daily Checklist Reminders

- **Before starting each day:** Pull latest changes, review previous day's TODOs
- **End of each day:** Commit with meaningful message, push to remote, update this checklist
- **Testing:** Write tests alongside implementation, not after
- **Documentation:** Update Swagger annotations as you build endpoints
- **Security:** Never commit secrets — use environment variables

---

## Progress Tracker

| Week | Day | Status | Notes |
|------|-----|--------|-------|
| 1 | Day 1 | ✅ | Project Init |
| 1 | Day 2 | ✅ | DB Migrations |
| 1 | Day 3 | ✅ | Entities |
| 1 | Day 4 | ✅ | DTOs & Common |
| 1 | Day 5 | ✅ | Docker |
| 2 | Day 6 | ✅ | Account Service |
| 2 | Day 7 | ✅ | Validation |
| 2 | Day 8 | ✅ | Transaction Service |
| 2 | Day 9 | ✅ | Retry & Failures |
| 2 | Day 10 | ✅ | History & Queries |
| 3 | Day 11 | ✅ | Kafka Producer |
| 3 | Day 12 | ✅ | Kafka Consumer |
| 3 | Day 13 | ✅ | Redis Caching |
| 3 | Day 14 | ✅ | JWT Auth |
| 3 | Day 15 | ✅ | Authorization |
| 4 | Day 16 | ✅ | Health Checks |
| 4 | Day 17 | ✅ | Metrics |
| 4 | Day 18 | ⬜ | Logging & Tracing |
| 4 | Day 19 | ⬜ | Testing |
| 4 | Day 20 | ⬜ | Notifications |
| 5 | Day 21 | ⬜ | Kubernetes |
| 5 | Day 22 | ⬜ | Performance |
| 5 | Day 23 | ⬜ | API Docs & Security |
| 5 | Day 24 | ⬜ | Final Testing & Docs |

---

*Replace ⬜ with ✅ as you complete each day.*
