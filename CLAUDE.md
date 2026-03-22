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

# Start infrastructure services (Redis, MySQL, Zipkin, RabbitMQ)
docker-compose up -d
```

## Architecture

**4-layer DDD structure:**
- `loan-web` - Controller layer, Spring Boot entry
- `loan-app` - Application services, RabbitMQ consumers
- `loan-domain` - Domain entities, repository interfaces (no external dependencies)
- `loan-infra` - MyBatis Plus persistence, Redis, implements repository interfaces

**Key patterns:**
- Rich domain model (充血模型) - business logic in entity classes
- State transition guards in `OrderStatus.canTransitionTo()`
- Optimistic locking with `version` field
- Repository pattern with MyBatis Plus
- `@Idempotent` AOP for distributed idempotency (Redis-based)
- `TransactionSynchronizationManager.afterCommit()` for reliable MQ publishing

**Message-driven workflow (Saga pattern):**
```
LoanApplicationService.applyLoan()
    → MQ: loan.order.created
    → LoanAuditConsumer (风控审批)
        → MQ: loan.order.approved (after commit)
        → LoanFundingConsumer (放款处理)
```

## Tech Stack

- Java 22
- Spring Boot 3.4.0
- MyBatis Plus 3.5.7
- Redis (Lettuce) - distributed rate limiting with Lua scripts
- MySQL 8.0
- RabbitMQ - message-driven state transitions
- Zipkin - distributed tracing (OpenTelemetry)
- Lombok + MapStruct
