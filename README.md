# Checkout Demo — Java Concurrency & Caching Interview Prep Project

A small, production-oriented Spring Boot service that demonstrates concepts that
show up constantly in senior Java backend interviews but rarely appear naturally
in a basic CRUD app: **`ConcurrentHashMap`**, **`CompletableFuture`**,
**Virtual Threads (Java 21+)**, **Redis caching**, and **PostgreSQL** — all wired
together around one realistic business scenario: **order checkout**.

> This is a simplified version of a production checkout flow — small enough to
> explain end-to-end in an interview, realistic enough that every concept has a
> genuine reason to exist.

---

## Business Scenario

A customer checks out a product. The service needs to:

1. Look up the product's price (frequently read, rarely changed → **Redis cache**)
2. Look up current stock and reserve it for this checkout (must be race-safe
   across concurrent checkouts → **`ConcurrentHashMap`**)
3. Fetch product, inventory, and customer info **concurrently** rather than one
   after another → **`CompletableFuture`**
4. Do all of the above I/O-bound work cheaply at scale → **Virtual Threads**
5. Persist the final order → **PostgreSQL / Spring Data JPA**

Nothing here was added just to "show off" a technology — each one solves a
specific problem in this flow, which is the story to tell in an interview.

---

## Architecture

```
Client
  │
  ▼
CheckoutController  (REST layer)
  │
  ├─► CheckoutOrchestratorService   (CompletableFuture: fetch product, inventory,
  │                                  customer concurrently, running on a
  │                                  virtual-thread-per-task Executor)
  │        │
  │        ├─► PricingCacheService  ──► Redis (cache-aside: hit / miss / populate)
  │        │                        └─► ProductRepository ──► PostgreSQL (on miss)
  │        │
  │        └─► InventoryRepository ──► PostgreSQL
  │
  ├─► InventoryReservationService   (ConcurrentHashMap: atomic in-flight
  │                                  reservation tracking, guards against
  │                                  overselling before the DB commit)
  │
  └─► OrderService ──► OrderRepository ──► PostgreSQL (final persisted order)
```

**Request flow in one sentence:** the controller gathers data concurrently,
atomically reserves stock in memory, then persists the order — rolling the
reservation back if persistence fails.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Java 21+ |
| Framework | Spring Boot 3.3.x |
| Build tool | Maven |
| Database | PostgreSQL (Spring Data JPA / Hibernate) |
| Cache | Redis |
| Concurrency | `java.util.concurrent` (`ConcurrentHashMap`, `CompletableFuture`, Virtual Threads) |
| Boilerplate reduction | Lombok |

---

## Prerequisites

- JDK 21+
- Maven 3.9+
- Docker (for local Postgres + Redis), or existing local instances

```bash
# Postgres
docker run --name checkout-postgres -p 5432:5432 \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=checkout_demo \
  -d postgres

# Redis
docker run --name checkout-redis -p 6379:6379 -d redis
```

Update `src/main/resources/application.yml` if your credentials/ports differ.

---

## Running the App

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. Hibernate will auto-create the
`products`, `inventory`, and `orders` tables on first run (`ddl-auto: update`
— fine for this demo; you'd use Flyway/Liquibase in real production).

### Seed sample data

```sql
INSERT INTO products (name, price) VALUES ('Wireless Mouse', 25.00);
INSERT INTO inventory (product_id, available_quantity) VALUES (1, 50);
```

(Adjust the `product_id` to match whatever ID Postgres assigned the inserted
product.)

---

## API

### `POST /api/checkout`

Runs the full flow: concurrent data fetch → atomic stock reservation → order
persistence.

**Request**
```bash
curl -X POST http://localhost:8080/api/checkout \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "customerId": 100, "quantity": 2}'
```

**Response — success (200)**
```json
{
  "id": 1,
  "productId": 1,
  "customerId": 100,
  "quantity": 2,
  "totalPrice": 50.00,
  "status": "CONFIRMED",
  "createdAt": "2026-09-06T10:15:30Z"
}
```

**Response — insufficient stock (409)**
```
Insufficient stock for product 1
```

### `GET /api/checkout/reservations/{productId}`

Returns how many units of a product are currently held in-flight (reserved but
not yet reconciled against the DB) — useful for demonstrating the
`ConcurrentHashMap` reservation tracker directly.

```bash
curl http://localhost:8080/api/checkout/reservations/1
```

---

## Project Structure

```
checkout-demo/
├── pom.xml
├── src/main/java/com/example/checkout/
│   ├── CheckoutDemoApplication.java
│   ├── config/
│   │   ├── RedisConfig.java              # RedisTemplate + JSON serialization
│   │   └── VirtualThreadConfig.java      # virtual-thread-per-task Executor bean
│   ├── entity/
│   │   ├── Product.java
│   │   ├── Inventory.java
│   │   └── Order.java
│   ├── repository/
│   │   ├── ProductRepository.java
│   │   ├── InventoryRepository.java
│   │   └── OrderRepository.java
│   ├── service/
│   │   ├── PricingCacheService.java             # Redis cache-aside
│   │   ├── InventoryReservationService.java     # ConcurrentHashMap
│   │   ├── CheckoutOrchestratorService.java     # CompletableFuture
│   │   └── OrderService.java
│   └── controller/
│       └── CheckoutController.java
└── src/main/resources/
    └── application.yml
```

---

## Concepts Demonstrated (Quick Reference)

| Concept | Where | Why it's there |
|---|---|---|
| `ConcurrentHashMap` | `InventoryReservationService` | Atomic `compute()`/`computeIfPresent()` for race-free stock reservation without locking the whole map |
| `CompletableFuture` | `CheckoutOrchestratorService` | Fetch product, inventory, and customer info concurrently instead of sequentially; `thenCombine` to merge typed results; `.exceptionally()` for a recoverable failure path |
| Virtual Threads | `VirtualThreadConfig`, `application.yml` (`spring.threads.virtual.enabled`) | Cheaply run I/O-bound checkout work (DB + Redis + simulated calls) at scale without platform-thread-pool tuning |
| Redis | `RedisConfig`, `PricingCacheService` | Cache-aside for read-heavy, rarely-changed pricing data; TTL bounds staleness |
| PostgreSQL | `entity/`, `repository/` | Source of truth for products, inventory, and orders |

For the full interview-ready 30–60 second explanations, common follow-up
questions, and "how I'd explain this project" walkthrough for each concept,
see the accompanying interview prep notes from our conversation.

---

## Known Simplifications (good "what I'd do differently in production" talking points)

- `ddl-auto: update` instead of Flyway/Liquibase migrations
- No circuit breaker around Redis calls (a Redis outage should degrade gracefully via a library like Resilience4j)
- In-flight reservations live in a single JVM's `ConcurrentHashMap` — doesn't coordinate across multiple app instances; a real multi-instance deployment would need Redis (`INCR`/Lua) or DB-level locking (`SELECT ... FOR UPDATE`) instead
- No background reconciliation job to decrement `Inventory.availableQuantity` and release reservations after a successful order
- No authentication/authorization on the endpoints
