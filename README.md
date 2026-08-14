# Booking Engine

A Spring Boot booking engine that handles product inventory queries, publishes order requests to RabbitMQ, processes them asynchronously, updates inventory in MariaDB, and caches product quantity reads in Redis.

## What this project is solving

This project addresses common scalability and reliability issues in order-processing systems:

- API latency protection: order processing is decoupled from the HTTP request by pushing order work onto RabbitMQ instead of doing expensive processing inline.
- Backpressure and queueing: RabbitMQ absorbs spikes and allows consumers to process work at a controlled rate.
- Duplicate and retry safety: consumers use manual acknowledgements and retry policies so messages are only acknowledged after successful processing.
- Inventory consistency under concurrent updates: optimistic locking is used to prevent lost updates when multiple orders try to reduce stock at the same time.
- Read-heavy scaling: product quantity reads are cached in Redis to reduce repeated database reads for hot inventory data.
- Observability: application logs and dedicated order audit logs are written to files so success/failure patterns can be traced.
- Connection tuning: HikariCP and Tomcat thread settings are configured for predictable pool behavior under moderate load.

## Architecture overview

- Spring Boot REST API exposes product and order endpoints
- RabbitMQ queue receives order messages
- Listener consumes messages and performs payment + order processing
- MariaDB stores inventory rows
- Redis caches GET inventory requests
- File logs capture application events and order audit events

## Project configuration

The application expects the following runtime services to be running locally:

- MariaDB on `localhost:3306`
- RabbitMQ on `localhost:5672`
- Redis on `localhost:6379`

These settings are currently configured in `src/main/resources/application.properties`:

```properties
spring.application.name=booking-engine

# MariaDB Connection Settings
spring.datasource.url=jdbc:mariadb://localhost:3306/booking_engine
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=5000

# JPA / Hibernate
spring.jpa.database-platform=org.hibernate.dialect.MariaDBDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# RabbitMQ Configuration
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.acknowledge-mode=manual

# Logging
logging.level.root=INFO
logging.file.name=logs/application.log

# Tomcat thread pool
server.tomcat.max-threads=300

# Redis configuration
spring.cache.type=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.cache.redis.time-to-live=60000
```

### RabbitMQ exchange and queue

The project declares the queue and binding in `src/main/java/com/example/booking_engine/config/RabbitMQConfig.java`:

```java
public static final String QUEUE_NAME = "orderQueue";
public static final String EXCHANGE_NAME = "orderExchange";
public static final String ROUTING_KEY = "orderRoutingKey";
```

This matches the application configuration used when publishing and consuming orders.

## Local services setup

## 1) MariaDB setup

### Option A: Docker

```bash
docker run -d \
  --name booking-mariadb \
  -e MARIADB_ROOT_PASSWORD=password \
  -e MARIADB_DATABASE=booking_engine \
  -p 3306:3306 \
  mariadb:11
```

Then verify connectivity:

```bash
mysql -h localhost -P 3306 -u root -ppassword
```

### Option B: Local install

Install MariaDB and create the database manually:

```sql
CREATE DATABASE booking_engine;
```

Then ensure the app credentials match:

- user: `root`
- password: `password`
- database: `booking_engine`

---

## 2) RabbitMQ setup

### Option A: Docker

```bash
docker run -d \
  --name booking-rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

This exposes:

- AMQP port: `5672`
- Management UI: `http://localhost:15672`
- default login: `guest / guest`

### Option B: Local install

Install RabbitMQ and ensure it is running on `localhost:5672`.

Important configuration for this project:

- queue name: `orderQueue`
- exchange: `orderExchange`
- routing key: `orderRoutingKey`
- manual acknowledgements enabled (`spring.rabbitmq.listener.simple.acknowledge-mode=manual`)
- prefetch set to `1` (`spring.rabbitmq.listener.simple.prefetch=1`) to reduce message throughput spikes and improve fairness

You can view and manage the queue in the RabbitMQ UI at:

```text
http://localhost:15672
```

---

## 3) Redis setup

### Option A: Docker

```bash
docker run -d \
  --name booking-redis \
  -p 6379:6379 \
  redis:7-alpine
```

### Option B: Local install

Install Redis and run it with the default port `6379`.

This project uses:

- host: `localhost`
- port: `6379`
- cache TTL: `60000` ms (`spring.cache.redis.time-to-live=60000`)

The cache is used on inventory read endpoints via `@Cacheable`.

---

## 4) Project run steps

From the project root:

```bash
./mvnw clean package
./mvnw spring-boot:run
```

On Windows, if needed:

```powershell
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

The app will start on the default Spring Boot port:

```text
http://localhost:8080
```

---

## API endpoints

### Get all products

```http
GET /products
```

### Get quantity for a product

```http
GET /products/{id}
```

This endpoint is cached in Redis via `@Cacheable(value="productQuantity", key="#id")`.

### Place an order

```http
POST /products/place-order/{id}/{userId}/{orderQuantity}
```

Example:

```http
POST /products/place-order/1/42/3
```

This publishes a message to RabbitMQ containing:

```json
{"productid":"1","userid":"42","quantity":"3"}
```

The listener then processes the message asynchronously and logs the outcome in the order audit file.

---

## Logging and audit output

The project writes application logs to:

```text
logs/application.log
```

Order audit logs are written to:

```text
logs/order-audit.log
```

These are configured in `src/main/resources/logback-spring.xml`.

---

## Scalability and resilience features included

### 1) Async processing
The booking endpoint accepts a request quickly and publishes to RabbitMQ, instead of blocking the API on inventory and payment work.

### 2) Manual message acknowledgement
The listener uses manual ack/nack, which is configured with:

```properties
spring.rabbitmq.listener.simple.acknowledge-mode=manual
```

This makes message handling more reliable; a message is only removed from the queue when processing is complete.

### 3) Prefetch and backpressure control
The app sets:

```properties
spring.rabbitmq.listener.simple.prefetch=1
```

This ensures a consumer does not pull too many messages ahead of time, which helps avoid unbounded processing spikes and gives better fairness during load.

### 4) Optimistic locking for inventory
The inventory entity is expected to include a version field to prevent lost updates when multiple concurrent orders modify the same inventory row.

### 5) Redis caching
Hot reads like product inventory level are cached to reduce repeated DB lookups and help the system scale under read-heavy traffic.

### 6) Hikari and Tomcat tuning
The app tunes the execution pool and HTTP server for practical concurrency:

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=5000
server.tomcat.max-threads=300
```

---

## Important operational notes

- RabbitMQ and Redis must be running before the application starts.
- MariaDB must exist with the configured database name and credentials.
- The app uses a durable queue with explicit message routing and manual acks.
- If you enable more concurrency in RabbitMQ listeners, keep the queue semantics and idempotency design in mind to avoid duplicate charges or inventory miscounts.
- For production, add stronger idempotency keys, payment-state persistence, and retry-safe order tracking.

---

## Quick start summary

```bash
docker run -d --name booking-mariadb -e MARIADB_ROOT_PASSWORD=password -e MARIADB_DATABASE=booking_engine -p 3306:3306 mariadb:11
docker run -d --name booking-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
docker run -d --name booking-redis -p 6379:6379 redis:7-alpine
./mvnw spring-boot:run
```

Then open:

- API: `http://localhost:8080`
- RabbitMQ UI: `http://localhost:15672` (`guest` / `guest`)

---

## Recommended next step

For a production-grade version, add:

- payment idempotency key per order
- persistent order state table
- exact retry strategy for transient failures
- stronger optimistic locking or version-aware inventory updates
- monitoring for queue depth, consumer lag, and cache hit ratio
