# Event-Driven E-Commerce Platform

A production-style backend system demonstrating microservices, event-driven architecture, and reliability patterns using Spring Boot, Kafka, Redis, and MySQL.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Messaging | Apache Kafka (KRaft, bitnami/kafka:3.9) |
| Cache / Rate Limiting | Redis 7.4 (AOF persistence) |
| Database | MySQL 8.4 (one per service) |
| Migrations | Liquibase |
| Security | Spring Security + JWT (custom HMAC-SHA256) |
| Build | Gradle |
| Containerization | Docker Compose |

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                             CLIENT                                   │
└────────────────────────────────┬────────────────────────────────────┘
                                 │ HTTP
                                 ▼
              ┌──────────────────────────────────┐
              │          API GATEWAY             │
              │           (port 8080)            │
              └──┬─────────┬────────┬────────────┘
                 │         │        │
                 ▼         ▼        ▼
          ┌──────────┐ ┌───────┐ ┌──────────┐
          │  User    │ │Product│ │  Order   │
          │  :8081   │ │ :8082 │ │  :8084   │
          └────┬─────┘ └───┬───┘ └────┬─────┘
               │           │          │
               │      ┌────┴────┐     │
               │      │  Redis  │     │
               │      │ Cache + │     │
               │      │  Rate   │     │
               │      └─────────┘     │
               │                      │
               └────────┬─────────────┘
                        │  Outbox Pattern → Kafka
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          APACHE KAFKA (KRaft)                        │
│                                                                      │
│  user-created ───────────────────────────────────► notification     │
│                                                                      │
│  order-created ──────────────────────────────────► inventory        │
│               └──────────────────────────────────► notification     │
│                                                                      │
│  inventory-reserved ─────────────────────────────► order            │
│                └─────────────────────────────────► notification     │
│                                                                      │
│  inventory-failed ───────────────────────────────► order            │
│               └──────────────────────────────────► notification     │
│                                                                      │
│  Retry chain (auto via @RetryableTopic):                             │
│  user-created-retry-0/1/2 ──► user-created-dlt                      │
│  order-created-retry-0/1/2 ──► order-created-dlt                    │
└─────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   MySQL — Database Per Service                       │
│  user_service │ product_service │ order_service │ inventory_service  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Architecture Diagram (Mermaid)

```mermaid
graph TD
    Client([Client]) --> GW[API Gateway :8080]

    GW --> US[User Service :8081]
    GW --> PS[Product Service :8082]
    GW --> OS[Order Service :8084]

    PS <-->|Cache-Aside TTL 10m| Redis[(Redis AOF)]
    US -->|Rate Limit login| Redis
    OS -->|Rate Limit checkout| Redis
    US -->|JWT Blacklist logout| Redis

    US -->|Outbox| UDB[(user_service DB)]
    OS -->|Outbox| ODB[(order_service DB)]
    PS --> PDB[(product_service DB)]

    UDB -->|Publisher Job 5s| Kafka{Apache Kafka KRaft}
    ODB -->|Publisher Job 5s| Kafka

    Kafka -->|user-created| NS[Notification :8085]
    Kafka -->|order-created| IS[Inventory :8083]
    Kafka -->|order-created| NS
    Kafka -->|inventory-reserved| OS
    Kafka -->|inventory-reserved| NS
    Kafka -->|inventory-failed| OS
    Kafka -->|inventory-failed| NS

    IS --> IDB[(inventory_service DB)]

    NS -->|@RetryableTopic 4 attempts| RT[retry-0 → retry-1 → retry-2 → DLT]

    style Kafka fill:#e8f4f8,stroke:#2196F3
    style Redis fill:#fff3e0,stroke:#FF9800
    style RT fill:#ffebee,stroke:#f44336
```

---

## Order Lifecycle (Full Event Flow)

```
POST /orders ──► order-service
                     │
                     │ @Transactional
                     ├── INSERT orders
                     ├── INSERT outbox (status=PENDING)
                     └── COMMIT
                              │
                              │ @Scheduled every 5s
                              ▼
                     OutboxPublisherService
                              │
                              ▼ kafkaTemplate.send
                     ┌────────────────────┐
                     │   order-created    │
                     └────────┬───────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
   inventory-service                 notification-service
   (consume order-created)           (consume order-created)
              │
              ├── stock available?
              │       │
              │       ├── YES ──► publish inventory-reserved
              │       │                    │
              │       │          ┌─────────┴─────────┐
              │       │          ▼                   ▼
              │       │   order-service        notification-service
              │       │   auto-CONFIRMED        "Order confirmed" email
              │       │
              │       └── NO ───► publish inventory-failed
              │                            │
              │                  ┌─────────┴─────────┐
              │                  ▼                   ▼
              │           order-service        notification-service
              │           auto-CANCELLED        "Order cancelled" email
              │
              └── mark processedEvent (idempotent)
```

---

## Services

### API Gateway `:8080`

Single entry point — routes all client requests to the correct downstream service. JWT validation happens per-service (the gateway is routing-only, it passes the `Authorization` header through as-is).

| Route | Forwards To |
|---|---|
| `/api/v1/users/**` | user-service:8081 |
| `/api/v1/products/**` | product-service:8082 |
| `/api/v1/inventory/**` | inventory-service:8083 |
| `/api/v1/orders/**` | order-service:8084 |
| `/api/v1/simulate/duplicate-event` | order-service:8084 |
| `/api/v1/simulate/notification/**` | notification-service:8085 |

---

### User Service `:8081`

Handles user registration, authentication, JWT generation, and token invalidation.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/users` | Public | Register a new user |
| POST | `/api/v1/users/login` | Public | Login — returns JWT (rate limited) |
| POST | `/api/v1/users/logout` | JWT | Blacklist token in Redis |
| GET | `/api/v1/users` | JWT | List all users |
| GET | `/api/v1/users/{id}` | JWT | Get user profile |
| PUT | `/api/v1/users/{id}` | JWT | Update user |
| DELETE | `/api/v1/users/{id}` | JWT | Delete user |

**Patterns:** Outbox → `user-created` on Kafka

**Redis:**
- `blacklist:<token>` — JWT blacklist on logout. TTL = remaining token validity. AOF persistence ensures blacklist survives Redis restarts.
- `rate:login:{ip}` — rate limit on POST /login (5 requests / 60s per IP)

---

### Product Service `:8082`

Manages the product catalog with Redis caching and trending tracking.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/products` | JWT | Create product |
| GET | `/api/v1/products` | JWT | List all products |
| GET | `/api/v1/products/{id}` | JWT | Get product (cached) |
| GET | `/api/v1/products/by-sku/{sku}` | JWT | Get by SKU |
| GET | `/api/v1/products/trending` | JWT | Top 10 trending products |
| PUT | `/api/v1/products/{id}` | JWT | Update product (evicts cache) |
| DELETE | `/api/v1/products/{id}` | JWT | Delete product (evicts cache) |

**Redis:**
- `products::{id}` — cache-aside, TTL 10 min. `@Cacheable` on GET, `@CacheEvict` on PUT/DELETE.
- `trending-products` — sorted set, score = view count. `ZINCRBY` on every GET /products/{id}.

---

### Order Service `:8084`

Creates and tracks orders. Reacts to inventory events automatically.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/orders` | JWT | Create order (rate limited) |
| GET | `/api/v1/orders` | JWT | List all orders |
| GET | `/api/v1/orders/{id}` | JWT | Get order |
| GET | `/api/v1/orders/by-user/{userId}` | JWT | Get orders by user |
| PUT | `/api/v1/orders/{id}/status` | JWT | Manually update status |
| POST | `/api/v1/orders/{id}/cancel` | JWT | Cancel order |

**Patterns:** Outbox → `order-created` on Kafka

**Kafka consumed:** `inventory-reserved` (auto-CONFIRMED), `inventory-failed` (auto-CANCELLED)

**Redis:** `rate:checkout:{userId}` — rate limit on POST /orders (10 requests / 60s per user)

---

### Inventory Service `:8083`

Manages stock levels. Pure event-driven — no REST APIs.

**Kafka consumed:** `order-created` — attempt to reserve stock

**Kafka produced:**
- `inventory-reserved` — stock successfully reserved
- `inventory-failed` — insufficient stock

**Patterns:** Idempotent Consumer — deduplicates by `eventId` UUID stored in `processed_events` table (same `@Transactional` as stock update).

---

### Notification Service `:8085`

Simulates email and SMS for all lifecycle events. No REST APIs.

**Kafka consumed:** `user-created`, `order-created`, `inventory-reserved`, `inventory-failed`

**Patterns:** `@RetryableTopic` (4 attempts, exponential backoff 1s→2s→4s) + Dead Letter Topic

```
[fail] ──► *-retry-0 (1s delay)
             ──► [fail] ──► *-retry-1 (2s delay)
                              ──► [fail] ──► *-retry-2 (4s delay)
                                              ──► [fail] ──► *-dlt
```

Idempotent Consumer — same deduplication pattern as inventory-service.

---

## Kafka Topics

| Topic | Producer | Consumer(s) | Retry? |
|---|---|---|---|
| `user-created` | user-service | notification-service | Via @RetryableTopic |
| `order-created` | order-service | inventory-service, notification-service | Via @RetryableTopic |
| `inventory-reserved` | inventory-service | order-service, notification-service | No |
| `inventory-failed` | inventory-service | order-service, notification-service | No |
| `*-retry-0/1/2` | Kafka auto (Spring) | notification-service | — |
| `*-dlt` | Kafka auto (Spring) | Manual inspection | Dead Letter |

---

## Outbox Pattern

Guarantees Kafka event is published **if and only if** the DB transaction commits. Eliminates dual-write problem.

```
┌──────────────────────────────────────────────────────┐
│ @Transactional                                        │
│   1. INSERT INTO orders (...)                         │
│   2. INSERT INTO outbox (eventId UUID, payload, ...)  │
│   3. COMMIT  ◄── both succeed or both fail            │
└──────────────────────────────────────────────────────┘
                        │
                        │  @Scheduled every 5s
                        ▼
┌──────────────────────────────────────────────────────┐
│ OutboxPublisherJob                                    │
│   SELECT TOP 50 FROM outbox WHERE status = 'PENDING'  │
│   FOR EACH event:                                     │
│     kafkaTemplate.send(topic, payload)                │
│     UPDATE outbox SET status = 'PUBLISHED'            │
└──────────────────────────────────────────────────────┘
```

**Implemented in:** user-service (`user-created`), order-service (`order-created`)

**Outbox table fields:** `id`, `event_id` (UUID), `aggregate_type`, `aggregate_id`, `event_type`, `payload`, `status`, `retry_count`, `created_at`, `published_at`

---

## Idempotent Consumer

Prevents duplicate processing when Kafka redelivers the same message (at-least-once delivery guarantee).

```
@KafkaListener
@Transactional
public void onEvent(String payload) {
    String eventId = root.path("eventId").asText();          // UUID from payload

    if (processedEventRepository.existsByEventId(eventId))   // check first
        return;                                              // skip duplicate

    // ... business logic (stock update, notification) ...

    processedEventRepository.save(new ProcessedEvent(eventId, eventType));
    // ↑ saved in SAME @Transactional — atomic with business logic
}
```

**Implemented in:** inventory-service, notification-service

**Why same transaction?** If service crashes after business logic but before saving the eventId, the transaction rolls back entirely. On retry, the event processes cleanly with no partial state.

---

## Redis Usage

| Feature | Key Pattern | Type | TTL | Service |
|---|---|---|---|---|
| Product cache | `products::{id}` | String (JSON) | 10 min | product-service |
| Trending products | `trending-products` | Sorted Set (score=views) | None | product-service |
| Login rate limit | `rate:login:{ip}` | Counter | 60s | user-service |
| Checkout rate limit | `rate:checkout:{userId}` | Counter | 60s | order-service |
| JWT blacklist | `blacklist:{token}` | String | Remaining token TTL | user-service |

**Cache-Aside flow:**
```
GET /products/{id}
       │
       ├─► Redis HIT  ──► return cached JSON
       │
       └─► Redis MISS ──► MySQL query ──► Redis SET (TTL 10m) ──► return response
```

**Rate Limiting flow (Redis Counter + TTL):**
```
POST /login
       │
       ├─► INCR rate:login:{ip}
       │   if count == 1: EXPIRE rate:login:{ip} 60
       │   if count > 5:  return 429 Too Many Requests
       │
       └─► proceed with login
```

**Redis persistence:** AOF enabled (`--appendonly yes`) — blacklist and rate limit counters survive restarts.

---

## Failure Scenarios

| Scenario | What Happens | How It's Handled |
|---|---|---|
| **Kafka down at publish** | Outbox events accumulate in DB | Publisher job retries every 5s; no event lost |
| **Kafka down at consume** | Consumer pauses, reconnects | Spring Kafka auto-reconnects; offset not committed = redelivery |
| **Notification failure** | Exception thrown in listener | `@RetryableTopic` routes to retry-0 → retry-1 → retry-2 → DLT |
| **Duplicate event delivery** | Same message delivered twice | Idempotent consumer checks `processed_events` by UUID — second delivery is a no-op |
| **Consumer crash mid-processing** | Offset not committed | Kafka redelivers on reconnect; idempotent consumer handles the duplicate |
| **Redis down (cache)** | Cache unavailable | `@Cacheable` falls back to MySQL transparently |
| **Redis down (rate limiter)** | Counter write fails | Fail-open: request proceeds (availability > strict limiting) |
| **Redis down (blacklist)** | Blacklist write fails after logout | Token works until natural expiry; AOF persistence covers planned restarts |
| **DB slow query** | Outbox publish delayed | Order creation returns 201 immediately; event publishes asynchronously |

### How to Simulate Each Scenario

#### 1. Notification Failure → Retry Chain → DLT
```bash
# Enable fail mode — every incoming notification event will throw
curl -X POST http://localhost:8085/api/v1/simulate/notification/fail

# Place an order (triggers order-created → notification-service receives it)
curl -X POST http://localhost:8084/api/v1/orders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"sku":"WIDGET-001","quantity":1,"unitPrice":29.99}]}'

# Watch notification-service logs — you'll see:
#   [SIMULATE] Notification failure mode active — throwing to trigger retry chain
#   Attempt 1 → order-created-retry-0 (after 1s)
#   Attempt 2 → order-created-retry-1 (after 2s)
#   Attempt 3 → order-created-retry-2 (after 4s)
#   Attempt 4 → order-created-dlt (message parked)

# Recover — new events process normally
curl -X POST http://localhost:8085/api/v1/simulate/notification/recover
```

#### 2. Duplicate Event Delivery → Idempotent Consumer Rejects
```bash
# Place an order first (creates an outbox event, publisher sends it to Kafka)
curl -X POST http://localhost:8084/api/v1/orders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"sku":"WIDGET-001","quantity":1,"unitPrice":29.99}]}'

# Replay the last published outbox event with the same eventId
curl -X POST http://localhost:8084/api/v1/simulate/duplicate-event

# Watch inventory-service and notification-service logs — both will log:
#   Duplicate event detected eventId=<uuid>, skipping
# The event is NOT processed a second time — idempotency in action
```

#### 3. Kafka Down → Outbox Proves Its Value
```bash
# Stop Kafka broker
docker stop event-based-e-commerce-kafka-1

# Place an order — still returns 201 (order saved + outbox event saved in same transaction)
curl -X POST http://localhost:8084/api/v1/orders ...

# Outbox event sits in DB with status=PENDING, retryCount increments every 5s

# Bring Kafka back — publisher flushes accumulated events within 5s
docker start event-based-e-commerce-kafka-1
# Watch order-service logs: "Published outbox event id=X eventId=Y"
```

#### 4. Redis Down → Fail-Open Rate Limiting
```bash
# Stop Redis
docker stop event-based-e-commerce-redis-1

# Login still works — RateLimiterService.isAllowed() returns true when Redis is down
curl -X POST http://localhost:8081/api/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123"}'

# Product cache falls back to MySQL — no 500 errors
curl -X GET http://localhost:8082/api/v1/products/1 -H "Authorization: Bearer <token>"

docker start event-based-e-commerce-redis-1
```

#### 5. Consumer Crash → Kafka Redelivery
```bash
# Stop inventory-service mid-flight
docker stop event-based-e-commerce-inventory-service-1

# Place an order — order-created event published to Kafka, but not yet consumed
curl -X POST http://localhost:8084/api/v1/orders ...

# Restart inventory-service — Kafka sees uncommitted offset, redelivers the message
docker start event-based-e-commerce-inventory-service-1
# Watch logs: inventory-service processes the event (idempotent check passes since it's genuinely new)
```

---

## Running Locally

### Prerequisites
- Docker + Docker Compose
- Java 21 (for local builds only)

### Start everything

```bash
docker compose up --build
```

Starts: Kafka (KRaft), Redis (AOF), 5 MySQL instances, 5 Spring Boot services.

### Service URLs

| Service | Direct URL | Via Gateway |
|---|---|---|
| API Gateway | — | http://localhost:8080 |
| User Service | http://localhost:8081 | http://localhost:8080/api/v1/users |
| Product Service | http://localhost:8082 | http://localhost:8080/api/v1/products |
| Inventory Service | http://localhost:8083 | http://localhost:8080/api/v1/inventory |
| Order Service | http://localhost:8084 | http://localhost:8080/api/v1/orders |
| Notification Service | http://localhost:8085 | — (no REST, Kafka consumer only) |

### End-to-End Test Flow

```bash
# 1. Register a user
curl -X POST http://localhost:8081/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123"}'

# 2. Login — get JWT
curl -X POST http://localhost:8081/api/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123"}'
# → copy the token from the response

# 3. Create a product
curl -X POST http://localhost:8082/api/v1/products \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"sku":"WIDGET-001","name":"Widget","price":29.99,"stockQuantity":100}'

# 4. Place an order
curl -X POST http://localhost:8084/api/v1/orders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"sku":"WIDGET-001","quantity":2,"unitPrice":29.99}]}'
# → inventory-service reserves stock
# → order auto-confirms via inventory-reserved event
# → notification-service logs confirmation email

# 5. Check trending products (after several GET /products/{id} calls)
curl -X GET http://localhost:8082/api/v1/products/trending \
  -H "Authorization: Bearer <token>"

# 6. Logout — blacklist the token
curl -X POST http://localhost:8081/api/v1/users/logout \
  -H "Authorization: Bearer <token>"
```

---

## Project Phases

| Phase | Goal | Status |
|---|---|---|
| Week 1 | Working APIs — User, Product, Order with JWT + MySQL | ✅ Complete |
| Week 2 | Microservice communication — Kafka event flow | ✅ Complete |
| Week 3 | Production patterns — Outbox, Idempotent consumers, Retry, DLT | ✅ Complete |
| Week 4 | Performance + Deployment — Redis, Rate Limiting, Docker Compose | ✅ Complete |
| Week 5 | Resilience + Observability — Failure Simulations, API Gateway, MDC Logging | 🔄 In Progress |

---

## Resume Highlights

- Built an event-driven e-commerce platform using Spring Boot, Kafka, Redis, MySQL, and Docker
- Implemented the **Outbox Pattern** to guarantee exactly-once event publishing without distributed transactions — events survive Kafka downtime via DB persistence
- Implemented **Kafka Retry Topics and Dead Letter Topics** (`@RetryableTopic`) for resilient notification processing with exponential backoff
- Implemented **Idempotent Consumers** using UUID-based deduplication stored atomically with business logic in the same `@Transactional`
- Implemented **Redis Cache-Aside** (product catalog, TTL 10min) and **Redis Rate Limiting** (login + checkout) with counter + TTL pattern
- Implemented **JWT token blacklist** in Redis (AOF-persisted) with TTL equal to remaining token validity for stateless logout
- Implemented **full order event loop**: order-created → inventory-reserved/failed → order auto-confirmed/cancelled
- Containerized all services using **Docker Compose** with health-checked dependencies (Kafka KRaft, Redis AOF, 5 MySQL instances)
- Built **live failure simulation endpoints** to demonstrate retry chain, idempotency, and fail-open behavior interactively
- Added **Spring Cloud Gateway** as a unified entry point on port 8080, routing to 4 services with environment-variable-driven URIs for docker vs local portability
