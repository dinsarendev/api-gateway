# Spring Cloud Gateway Project
# Getting Started
A Spring Boot 3.4.5  Spring Cloud Gateway application for routing and securing microservices.
It supports dynamic route configuration in database and store and store redis cache for validate route, JWT authentication, and integration with service .

### 🚀 Features

- Reverse Proxy Gateway for multiple microservices
- Routing
- Predicates
- Filters
- Circuit Breaker
- Rate Limiting
- Authentication & Authorization
- Custom Filters

### 🛠️ Prerequisites
1. Intellij IDEA
   - Install the Lombok plugin for better code generation support.
   - Enable annotation processing in the IDE settings. 
2. Java 21 or higher
   - Ensure Java JDK 21 is installed and configured in your IDE.
   - Set the project SDK to Java 21 in your IDE.
3. PostgreSQL 16 or higher
   - Ensure PostgreSQL is running and accessible.
   - Create a database named `default_db` or change the configuration to match your database name. 
4. Gradle 8.0.0 or higher
   - Ensure Gradle is installed and configured properly.
5Docker (optional, for running Redis and PostgreSQL)

### 📂 Project Structure

```agsl
spring-cloud-gateway/
|── src/
│   ├── main/
│   │   ├── java/kh/com/personalbanking/apigatway/
│   │   │   ├── ApiGatewayApplication.java
│   │   │   ├── caches/        # Store, Redis cache
│   │   │   ├── config/        # CORS, Route configs, filters, and predicates
│   │   │   └── constants/     # Constants for the application
│   │   │   ├── controllers/   # fallback gateway
│   │   │   ├── dto/           # Data Transfer Objects / Request and Response models
│   │   │   ├── exceptions/    # Exception handling
│   │   │   ├── models/        # Store Entities
│   │   │   ├── register/      # Service registration and discovery
│   │   │   ├── service/       # Service layer for business logic
│   │   │   ├── repositories/  # Repositories for data access
│   │   │   ├── startup/       # Startup logic when application starts
│   │   │   └── utils/         # Token utilities
│   │   └── resources/
│   │       ├── application.yml # configuration server
│   │       └── application-local.yml (if using config run local)
│── build.gradle
│── Dockerfile
│── docker-compose.yml
│── README.md
```
### ⚙️ Configuration

#### 1. `application.yml`
```agsl
spring:
  config:
    import: "optional:configserver:http://localhost:8081/"
  application:
    name: api-gateway
  profiles:
    active: local
  cloud:
    compatibility-verifier:
      enabled: false
logging:
  level:
    org.springframework.cloud.config: DEBUG
```
create file `application-local.yml` in the same directory as `application.yml` for local development.
#### 2. `application-local.yml`
```agsl
server:
  port: 25010

spring:
  application:
    name: api-gateway
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/DB_NAME
    password: password
    username: username
  hikari:
    maximum-pool-size: 10
    minimum-idle: 10
    max-lifetime: 1800000
    idle-timeout: 600000
    connection-timeout: 600000
    leak-detection-threshold: 300000
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 30000
      password: password
      ssl:
        enabled: false
  spring:
    zipkin:
      base-url: http://alloy:9411
logging:
  enable: true
  includeRequest: true
  includeRequestBody: true
  maskLogKey: maskingKey001,maskingkey001,masking-key-002 # its key is sensitive
  ignoreDefaultMaskingLogKey: ignoreMaskingKey001,ignoreMasking-key-002
  includeRequestHeader: true
  maxRequestBody: 1000 # its value is byte
  ignoreAntMatches: /abc,/zte-ba/**,/hiding-log # this value just example
  includeResponse: true
  includeResponseHeader: true
  maskResponseHeaderKey: maskingKey001,maskingkey001,masking-key-002 # its key isn't sensitive
  includeResponseBody: true
  maxResponseBody: 200 # its value is byte, the log size response
  pattern:
    level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
    console: "[%d] [%t] %-5level [${spring.application.name},%X{traceId},%X{spanId},%X{activityid}] %logger{36} - %msg%n"
otel:
  exporter:
    otlp:
      endpoint: http://alloy:4318
      protocol: http/protobuf
  logs:
    exporter: none
  metrics:
    exporter: none
  traces:
    sampler:
      probability: 1.0
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus, refresh,env,loggers
authentication:
  jwtExpiration: 600000
  jwtRefreshExpiration: 86400000
  jwtSecret: ebe8910a-08c0-11ed-861d-0242ac120002
storage:
  redis:
    key-response-code: key-response-code
    key-api-route: key-api-route
aes-cipher:
  gcm:
    key: UsLHzxoYLfUZ6sFbPs906dWmf3tulwJs/MfMAvT8SLk=
    algorithm: AES/GCM/NoPadding
    iv: ZGRFq4ZfB20=
    tag-size: 128
```
### 🛠️ Build and Run PostgreSQL and Redis with Docker
#### 3. `postgres-docker-compose.yml`
```aidl
version: "3.3"
services:
  apigateway-db:
    image: postgres:16
    container_name: apigateway-db
    restart: always
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: 123
      POSTGRES_DB: default_db
    ports:
      - "5425:5432"
    networks:
      - apigateway-pg-network
```
```bash
  docker compose -f postgres-docker-compose.yml up -d
```
#### 5. `redis-docker-compose.yml`
```aidl
version: '3.8'
services:
  redis:
    image: redis:7.0.11  # Use a stable Redis version (change as needed)
    container_name: redis-server
    command: ["redis-server", "/usr/local/etc/redis/redis.conf"]
    volumes:
      - ./redis.conf:/usr/local/etc/redis/redis.conf:ro
      - redis-data:/data
    ports:
      - "6379:6379"
    restart: unless-stopped

volumes:
  redis-data:
```
add the `redis.conf` file in the same directory as your `redis-docker-compose.yml` file.
```aidl
# redis.conf
# Require password to authenticate
requirepass 123
# Save data to disk every 60 seconds if at least 1000 changes
save 60 1000
# Append only file for durability (AOF)
appendonly yes
# Location of the appendonly file
dir /data
```
```bash
  docker compose -f redis-docker-compose.yml up -d
```
### Create Database and Tables
You can use the following SQL script to create the necessary tables in your PostgreSQL database:

```sql
CREATE TABLE gateway.api_group_route (
    id bigserial NOT NULL,
    code varchar(255) NULL,
    uri varchar(255) NULL,
    updated_at timestamp(6) NULL,
    updated_by varchar(255) NULL,
    created_at timestamp(6) NULL,
    created_by varchar(255) NULL,
    status varchar(255) NULL,
    CONSTRAINT api_group_route_pkey PRIMARY KEY (id)
);
CREATE TABLE gateway.api_route (
    id bigserial NOT NULL,
    group_code varchar(255) NULL,
    application_id varchar(255) NULL,
    description varchar(255) NULL,
    is_public varchar(255) NULL,
    "method" varchar(255) NULL,
    "path" varchar(255) NULL,
    rate_limit int4 NULL,
    rate_limit_duration int4 NULL,
    created_at timestamp(6) NULL,
    created_by varchar(255) NULL,
    status varchar(255) NULL,
    updated_at timestamp(6) NULL,
    updated_by varchar(255) NULL,
    remark varchar NULL,
    is_encrypt varchar(1) DEFAULT 'N'::character varying NULL,
    priority int4 DEFAULT 1 NULL,
    start_time timestamp NULL,
    end_time timestamp NULL,
    enable_circuit_breaker varchar(1) DEFAULT 'Y'::character varying NULL,
    CONSTRAINT api_route_pkey PRIMARY KEY (id)
);
--- Simple data insert api_group_route
INSERT INTO gateway.api_group_route (code, uri, updated_at, updated_by, created_at, created_by, status) VALUES('AUTH', 'http://authentication-server:25011', 'NULL', NULL, '2025-06-12 18:37:57.050', 'SYS', 'ACT');
---- Simple data insert api_route
INSERT INTO api_route (group_code, application_id, description, is_public, "method", "path", rate_limit, rate_limit_duration, created_at, created_by, status, updated_at, updated_by, remark, is_encrypt, priority, start_time, end_time, enable_circuit_breaker) VALUES('AUTH', 'LOS', 'Authentication Access Token', 'Y', 'POST', '/oauth/token', 1000, 5, '2025-06-12 18:45:56.733', 'hengkang', 'ACT', '2025-06-12 18:45:56.733', 'SYS', 'PRO', 'N', 1, 'NULL', 'NULL', 'N');
```

### ▶️ Running the Project
### 1️⃣ Clone the repository

```aidl
  git clone git@github.com:Cambofreelance-Software-Development/api-gateway.git
  cd api-gateway
```
### 2️⃣ Build & Run

```aidl
  ./gradlew clean build
  ./gradlew bootRun
```

### 📦 Dependencies
```xml
  implementation 'org.springframework.boot:spring-boot-starter-actuator'
  implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
  implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
  implementation 'org.springframework.boot:spring-boot-starter-webflux'
  implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
  implementation 'io.micrometer:micrometer-tracing'
  implementation 'io.micrometer:micrometer-tracing-bridge-otel'
  implementation 'io.opentelemetry:opentelemetry-sdk'
  implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
  implementation "io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter"
  implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
  compileOnly 'org.projectlombok:lombok'
  runtimeOnly 'org.postgresql:postgresql'
  runtimeOnly 'org.postgresql:r2dbc-postgresql'
  annotationProcessor 'org.projectlombok:lombok'
  testImplementation 'org.springframework.boot:spring-boot-starter-test'
  testImplementation 'io.projectreactor:reactor-test'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
  implementation 'org.springframework.cloud:spring-cloud-starter-config'
  implementation 'org.apache.commons:commons-lang3'
  implementation 'com.googlecode.json-simple:json-simple:1.1.1'
  implementation 'commons-codec:commons-codec:1.15'
  implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
  implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
  runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
  runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5' // or jjwt-gson if you prefer
  implementation("org.mindrot:jbcrypt:0.4")
```
### 📜 License MIT License
Cambofreelance.com is licensed