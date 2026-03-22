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
- Resilience4j - circuit breaker
- Prometheus + Grafana - monitoring

## 安全规范

**严禁在 Git 跟踪的文件中写入明文账号密码**，包括但不限于：
- 配置文件（`.yml`、`.properties`、`.xml`）
- 代码注释
- SQL 文件
- README 等文档

**正确做法**：使用环境变量占位符 `${ENV_VAR}`，敏感信息通过以下方式管理：
- `.env` 文件（已加入 `.gitignore`）
- CI/CD  Secrets
- 容器编排的 Secret 对象

**不暴露本地部署的域名和端口号**。仅描述组件名称和容器名称（如 MySQL、Redis），不写具体连接地址。
