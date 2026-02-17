# Seckill Lab

> 当前仓库仅主维护中文文档，英文翻译将在后续版本统一补齐。

Seckill Lab 是一个面向初学者的电商秒杀教学仓库。项目从一个故意不完美的版本开始，先复现问题，再逐步优化，帮助你建立高并发系统的工程化思维。

当前基线版本：Spring Boot 4.0.x + Java 25。

## 为什么选秒杀做教学场景

秒杀把分布式系统中最关键的难点集中到了一个相对简单的业务流程中：

- 流量瞬时爆发，系统瓶颈更容易暴露。
- 库存正确性要求高，超卖和重复下单问题非常直观。
- 业务规则不复杂，适合循序渐进教学。
- 每次优化都可以通过压测和指标做清晰对比。

## 快速开始

### 0）准备环境

- IntelliJ IDEA
- Java 25（IDEA Project SDK 设为 25）
- Docker + Docker Compose
- Maven 3.9+（可选，主要用于执行测试命令）

### 1）先用 Docker 启动中间件

```bash
docker compose -f deploy/docker-compose.yml up -d
```

中间件端口映射（宿主机 -> 容器）：

- MySQL：`8081 -> 3306`
- Redis：`8082 -> 6379`
- RabbitMQ AMQP：`8083 -> 5672`
- RabbitMQ 管理台：`8084 -> 15672`

### 2）再用 IDEA 启动应用（默认 v0）

1. 用 IDEA 打开仓库根目录 `seckill-lab/`
2. 确认 `Project SDK = 25`
3. 打开 `seckill-app/src/main/java/com/seckill/lab/SeckillLabApplication.java`
4. 点击运行 `SeckillLabApplication`

如需体验 `v1`，在 Run Configuration 里设置：

- `Active profiles`: `v1`
- 或 Program arguments: `--spring.profiles.active=v1`

### 3）确认服务状态

```bash
curl "http://localhost:8080/api/stages/current"
```

### 4）复现 v0 并发问题

先重置库存：

```bash
curl -X POST "http://localhost:8080/api/v0/activities/1001/reset?stock=50"
```

再打突发流量：

```bash
bash scripts/v0-burst.sh 1001 300 60
```

重点看返回中的两个字段：

- `oversoldUnits`：可能大于 `0`（表示出现超卖）
- `duplicateUserCount`：可能大于 `0`（表示出现重复下单）

## 建议学习路径

1. 先看文档导航：[docs/README.md](./docs/README.md)
2. 先补基础：[docs/fundamentals/prerequisites.md](./docs/fundamentals/prerequisites.md)
3. 再看概念：[docs/fundamentals/core-concepts.md](./docs/fundamentals/core-concepts.md)
4. 阅读并完成 v0：[docs/chapters/v0-naive.md](./docs/chapters/v0-naive.md)
5. 稳定复现后再进入 `v1`

## 仓库结构

```text
seckill-lab/
  docs/                    # 中文教学文档
  deploy/                  # docker-compose 等部署文件
  scripts/                 # 演示与压测脚本
  seckill-app/             # 单体应用（前期教学主工程）
```

## 应用内包结构（阶段内分层）

- `com.seckill.lab.common`：通用响应与异常处理。
- `com.seckill.lab.stage`：阶段目录与阶段元信息接口（`/api/stages`）。
- `com.seckill.lab.stage.v0.controller`：v0 接口层。
- `com.seckill.lab.stage.v0.service`：v0 业务层。
- `com.seckill.lab.stage.v0.model`：v0 请求/响应模型。

后续 `v1+` 按同样模式扩展，保持“阶段内分层、阶段间隔离”。

## 演进路线图

| 阶段 | 名称 | 重点 | 状态 |
|---|---|---|---|
| v0 | Naive Sync Order | 复现超卖与重复下单 | Available |
| v1 | DB Guard | 唯一约束 + 乐观锁 | Planned |
| v2 | Redis Stock | Redis 热点库存 + Lua 原子扣减 | Planned |
| v3 | Async Order | MQ 异步下单、削峰填谷 | Planned |
| v4 | Idempotency | 请求与消费幂等去重 | Planned |
| v5 | Compensation | 超时关单与库存回补 | Planned |
| v6 | Anti Bot | 防刷风控与限流策略 | Planned |
| v7 | Observability | 指标、链路追踪与看板 | Planned |
| v8 | Scale Out | 可选微服务拆分演示 | Planned |

## 当前 v0 接口

- `POST /api/v0/activities/{activityId}/reset?stock=50`
- `POST /api/v0/activities/{activityId}/attempt`
- `GET /api/v0/activities/{activityId}/snapshot`
- `GET /api/stages`
- `GET /api/stages/current`

## 配置说明

- 默认 profile 为 `v0`（排除 DB/Flyway 自动装配）
- `GET /api/stages/current` 会根据当前 `active/default profile` 返回当前阶段
- 开始体验 `v1`（DB 版）时，在 IDEA Run Configuration 中设置 `Active profiles = v1`

## 常见问题

- Java 不是 25：先切换 `JAVA_HOME`
- 端口冲突（8081/8082/8083/8084）：关闭本机同端口服务后重试
- Docker 未启动：先启动 Docker Desktop 或 Docker 服务

## 下一步

实现 `v1`：基于 MySQL 的一人一单唯一约束 + 库存乐观锁扣减。
