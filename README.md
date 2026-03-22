# Loan Demo - 借款订单系统

基于 Spring Boot 3.4 的借款订单管理平台，采用 DDD 架构，支持高并发压测验证。

## 项目架构

```
loan-demo/
├── loan-web/          # Web 层：Controller、异常处理、全局配置
├── loan-app/           # 应用层：Service、MQ Consumer、Outbox调度、对账服务
├── loan-domain/        # 领域层：Entity、Repository接口、枚举（无外部依赖）
├── loan-infra/        # 基础设施层：MyBatis Plus、Redis、MQ、熔断器
├── sql/               # 数据库 DDL
└── docker-compose.yml  # 基础设施编排
```

## 业务流程（ Saga 模式）

```
用户请求
    │
    ▼
LoanApplicationService.applyLoan()
    │  ① Redis 预扣额度（Lua 原子操作）
    │  ② MySQL 持久化订单
    │  ③ Outbox 表写入消息（与订单同事务）
    │  ④ 事务提交后 → OutboxScheduler.trySendImmediately() 快速发送 MQ
    │
    ▼ MQ: loan.order.created
LoanAuditConsumer（风控审批）
    │  ① MySQL 实扣额度
    │  ② 状态 → APPROVED/REJECTED
    │  ③ 事务提交后 → 发送 MQ
    │
    ▼ MQ: loan.order.funding
LoanFundingConsumer（放款处理）
    │  ① 幂等校验（Redis 分布式锁）
    │  ② 调用 FundingClient（Resilience4j 熔断保护）
    │  ③ 状态 → PAYING → SUCCESS/FAILED
    │
    ▼
放款完成
```

## 核心功能

| 功能 | 实现方案 |
|------|----------|
| 借款申请 | `LoanApplicationService.applyLoan()` - Redis 预扣 → MySQL 持久化 → Outbox 可靠消息 |
| 幂等防重 | `@Idempotent` AOP - Redis SETNX 分布式锁，60s 过期 |
| 预扣额度 | Redis Lua 脚本原子扣减，支持懒加载 DB 额度 |
| 可靠消息 | Transactional Outbox - 消息与订单同事务，补偿机制确保 100% 投递 |
| 事务补偿 | `TransactionSynchronization.afterCompletion(ROLLBACK)` 精准释放预扣额度 |
| 分布式锁 | `RedisLockManager` - 定时任务防惊群，Outbox 调度防并发 |
| 熔断保护 | Resilience4j `@CircuitBreaker` - 外部放款接口失败率 >50% 自动熔断 |
| 全链路追踪 | Micrometer Tracing + Brave + Zipkin，traceId 通过 MQ W3C header 传播 |
| 缓存策略 | 防穿透（空值标记）+ 防击穿（分布式锁 single-flight）+ 防雪崩（随机 TTL + 预热） |
| 缓存预热 | `CacheWarmUpService` - 启动时加载 + 每小时刷新即将过期的 key |
| 业务对账 | `LoanReconciliationService` - 每5分钟扫描 APPROVED/PAYING 卡单，主动触发放款 |
| Outbox 对账 | 每1分钟扫描 PENDING 积压消息，重新发送 |
| 乐观锁 | `version = version + 1` 数据库原子操作，防止并发覆盖 |

## 技术栈

| 分类 | 技术 | 说明 |
|------|------|------|
| 基础框架 | Java 22 + Spring Boot 3.4.0 | |
| 持久化 | MyBatis Plus 3.5.7 | 乐观锁、自动填充、Lambda 查询封装 |
| 缓存 | Redis 7 (Lettuce) | Lua 脚本原子操作、分布式锁 |
| 消息队列 | RabbitMQ 3 | 延迟队列（DLX）、可靠投递、消费者幂等 |
| 数据库 | MySQL 8.0 | DDL 位于 `sql/schema.sql` |
| 链路追踪 | Micrometer Tracing + Brave + Zipkin | W3C 传播模式，traceId 透传 |
| 熔断限流 | Resilience4j 2.3.0 | `@CircuitBreaker` + `@TimeLimiter` |
| 监控 | Prometheus + Grafana | `micrometer-registry-prometheus` 暴露指标 |
| 对象映射 | MapStruct + Lombok | PO ↔ Entity 转换 |
| 幂等 | 自定义 `@Idempotent` AOP | Redis SETNX |

## 快速启动

### 1. 启动基础设施

```bash
docker-compose up -d
```

启动以下容器：MySQL、Redis、RabbitMQ、Zipkin、Prometheus、Grafana

### 2. 初始化数据库

```bash
# 通过 docker-compose exec 进入 MySQL 容器后执行
mysql -u root -p loan_db < /sql/schema.sql
```

### 3. 启动应用

```bash
mvn clean package -DskipTests -pl loan-web,loan-app
mvn spring-boot:run -pl loan-web
```

### 4. 验证接口

```bash
curl -X POST http://localhost:8080/api/loan/apply \
  -H "Content-Type: application/json" \
  -d '{"userId":1003,"amount":1000,"orderNo":"REQ_20240520_001"}'
```

## 关键配置文件

### 环境变量

| 变量 | 说明 |
|------|------|
| `MYSQL_HOST` | MySQL 地址 |
| `MYSQL_PORT` | MySQL 端口 |
| `MYSQL_USERNAME` | 用户名 |
| `MYSQL_PASSWORD` | 密码 |
| `REDIS_HOST` | Redis 地址 |
| `REDIS_PORT` | Redis 端口 |
| `RABBITMQ_HOST` | RabbitMQ 地址 |
| `RABBITMQ_PORT` | RabbitMQ 端口 |

### actuator 端点

| 端点 | 地址 |
|------|------|
| Prometheus 指标 | `GET /actuator/prometheus` |
| 健康检查 | `GET /actuator/health` |
| 应用信息 | `GET /actuator/info` |
| JVM 指标 | `GET /actuator/metrics` |

## 监控看板

### Grafana 面板推荐

导入以下 Dashboard ID：
- **4701** - JVM Micrometer（QPS、RT、GC）
- **12900** - Resilience4j 熔断器状态

### 常用 PromQL

```promql
# 实时 QPS
rate(http_server_requests_seconds_count[1m])

# P99 响应时间
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))

# 熔断器状态（1=CLOSED, 2=OPEN, 3=HALF_OPEN）
resilience4j_circuitbreaker_state{name="fundingService"}

# 数据库连接池等待
hikaricp_connections_pending{pool="HikariPool-1"}

# Redis 预扣成功率
rate(redis_executor_commands_success_total[1m])
```

## 数据库表

| 表名 | 用途 |
|------|------|
| `t_loan_order` | 借款订单，状态机流转 |
| `t_user_limit` | 用户额度，Redis 缓存源 |
| `t_message_outbox` | 本地消息表，Transactional Outbox |

关键索引：
- `t_loan_order`: `(status, update_time)` - 对账查询
- `t_message_outbox`: `(status, next_retry_time)` - 积压消息扫描

## 领域模型

### 订单状态机

```
INIT → APPROVED → PAYING → SUCCESS
                ↘ REJECTED
              ↘ FAILED
```

### Redis 预扣流程

```
tryAcquireLimit(userId, amount)
    ↓
Redis 命中？ → 否 → 从 MySQL 懒加载（分布式锁保护）
    ↓
Lua 脚本：available >= amount ? INCRBYFLOAT(-amount) : return 0
    ↓
返回 true/false
```

## 常见问题排查

| 现象 | 可能原因 |
|------|----------|
| 订单创建成功但 MQ 没发 | Outbox `id` 未回填，`updateStatus` 0 行更新 |
| 消费者重复扣减额度 | `@Idempotent` 锁 key 未生效，需检查 Redis 连接 |
| 全链路 traceId 断裂 | `observation-enabled` 未开启，或 Brave/OTel 版本冲突 |
| 熔断器未触发 | `slidingWindowSize` 太小，测试流量未达到阈值 |
| 缓存预热未生效 | `status=1` 查询条件不匹配，需确认数据库中用户状态值 |
