# Seckill Lab

> 当前仓库仅主维护中文文档，英文翻译将在后续版本统一补齐。

Seckill Lab 是一个面向初学者的电商秒杀教学仓库。项目从一个故意不完美的版本开始，先复现问题，再逐步优化，帮助你建立高并发系统的工程化思维。

当前基线版本：Spring Boot 4.0.x + Java 25。

## 为什么还要做秒杀教学项目

秒杀题材看起来“烂大街”，但它长期高频出现并不是因为简单，而是因为它把高并发系统里最关键的一组矛盾集中在了一个可观察的业务里：短时流量冲击和强一致性要求同时存在。电商秒杀、抢券、预约、配额分配场景虽然名字不同，本质都是有限资源竞争，因此超卖、重复下单、请求堆积、削峰和幂等这些问题会非常直观地暴露出来。

真正有挑战的不是“找到一个能跑的秒杀项目”，而是建立完整的工程判断力。很多教程会直接给 Redis、MQ、分布式锁等标准答案，代码能运行，却缺少关键上下文：为什么先做这一步、为什么这个改动真的有效、如何用数据证明系统变好了。结果往往是会抄方案，不会诊断问题，也很难迁移到新的业务场景。

Seckill Lab 的目标就是把这段最容易被跳过的学习过程补齐：从故意有缺陷的 v0 开始，先复现问题，再逐步改造，并要求每一步都有压测与指标作为证据。这样学完之后，得到的不只是某个场景的实现代码，而是一套可迁移的方法：先识别正确性风险，再做渐进式改造，最后用可验证结果证明改造价值。

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

启动完成后可访问 RabbitMQ 管理台：`http://localhost:8084`（默认 `guest/guest`）。

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
bash scripts/burst.sh v0 1001 300 60
```

重点看返回中的两个字段：

- `oversoldUnits`：可能大于 `0`（表示出现超卖）
- `duplicateUserCount`：可能大于 `0`（表示出现重复下单）

如需验证 `v1`（先在 IDEA 切到 `v1` profile）：

```bash
bash scripts/burst.sh v1 1001 300 60
```

该脚本会输出“吞吐量（请求/秒）”与“延迟 P50/P95/P99”等中文统计，便于同时观察正确性和响应速度。

如需验证 `v2`（先在 IDEA 切到 `v2` profile）：

```bash
bash scripts/burst.sh v2 1001 300 60
```

该脚本会输出“吞吐量（请求/秒）”与“延迟 P50/P95/P99”等中文统计，便于同时观察正确性和响应速度。

实验结束后可停止中间件：

```bash
docker compose -f deploy/docker-compose.yml down
```

## 建议学习路径

1. 先看文档导航：[docs/README.md](./docs/README.md)
2. 先补基础：[docs/fundamentals/prerequisites.md](./docs/fundamentals/prerequisites.md)
3. 再看概念：[docs/fundamentals/core-concepts.md](./docs/fundamentals/core-concepts.md)
4. 阅读并完成 v0：[docs/chapters/v0-naive.md](./docs/chapters/v0-naive.md)
5. 阅读并完成 v1：[docs/chapters/v1-db-guard.md](./docs/chapters/v1-db-guard.md)
6. 阅读并完成 v2：[docs/chapters/v2-redis-stock.md](./docs/chapters/v2-redis-stock.md)

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
| v0 | Naive Sync Order | 复现超卖与重复下单 | 可用 |
| v1 | DB Guard | 唯一约束 + 乐观锁 | 可用 |
| v2 | Redis Stock | Redis 热点库存 + Lua 原子扣减 | 可用 |
| v3 | Async Order | MQ 异步下单、削峰填谷 | 规划中 |
| v4 | Idempotency | 请求与消费幂等去重 | 规划中 |
| v5 | Compensation | 超时关单与库存回补 | 规划中 |
| v6 | Anti Bot | 防刷风控与限流策略 | 规划中 |
| v7 | Observability | 指标、链路追踪与看板 | 规划中 |
| v8 | Scale Out | 可选微服务拆分演示 | 规划中 |

## 当前接口

- `POST /api/v0/activities/{activityId}/reset?stock=50`
- `POST /api/v0/activities/{activityId}/attempt`
- `GET /api/v0/activities/{activityId}/snapshot`
- `POST /api/v1/activities/{activityId}/reset?stock=50`
- `POST /api/v1/activities/{activityId}/attempt`
- `GET /api/v1/activities/{activityId}/snapshot`
- `POST /api/v2/activities/{activityId}/reset?stock=50`
- `POST /api/v2/activities/{activityId}/attempt`
- `GET /api/v2/activities/{activityId}/snapshot`
- `GET /api/stages`
- `GET /api/stages/current`

## 配置说明

- 默认 profile 为 `v0`（排除 DB/Flyway 自动装配）
- `GET /api/stages/current` 会根据当前 `active/default profile` 返回当前阶段
- 开始体验 `v1`（DB 版）时，在 IDEA Run Configuration 中设置 `Active profiles = v1`
- 开始体验 `v2`（Redis 库存版）时，在 IDEA Run Configuration 中设置 `Active profiles = v2`
- `v1/v2` 无需手工建表：应用启动时会由 Flyway 自动执行 `seckill-app/src/main/resources/db/migration/V1__init_schema.sql`

## 常见问题

- 端口冲突（8081/8082/8083/8084）：关闭本机同端口服务后重试
- Docker 未启动：先启动 Docker Desktop 或 Docker 服务
- IDEA 报 `log` 相关编译错误：启用 Annotation Processing（`Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors`）

## 下一步

进入 `v3`：引入 MQ 异步下单，继续提升洪峰抗压能力。
