# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.4.0 + Maven multi-module DDD project for loan order management.

## Commands

```bash
# Build all modules
mvn clean package -DskipTests

# Run single module
mvn spring-boot:run -pl loan-web

# Run tests
mvn test

# Run specific test
mvn test -Dtest=LoanOrderRepositoryImplTest

# Start infrastructure services (Redis, MySQL, Zipkin)
docker-compose up -d
```

## Architecture

**4-layer DDD structure:**
- `loan-web` - Controller layer, Spring Boot entry
- `loan-app` - Application services
- `loan-domain` - Domain entities, repository interfaces (no external dependencies)
- `loan-infra` - MyBatis Plus persistence, Redis, implements repository interfaces

**Key patterns:**
- Rich domain model (充血模型) - business logic in entity classes
- State transition guards in `OrderStatus.canTransitionTo()`
- Optimistic locking with `version` field
- Repository pattern with MyBatis Plus

## Tech Stack

- Java 22
- Spring Boot 3.4.0
- MyBatis Plus 3.5.7
- Redis (Lettuce) - distributed rate limiting with Lua
- MySQL 8.0
- Zipkin - distributed tracing (OpenTelemetry)
- Lombok + MapStruct
