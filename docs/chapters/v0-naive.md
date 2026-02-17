# v0 - 朴素同步秒杀

这一章是教学中的“故意不安全版本”。
目标不是先修好，而是先稳定复现问题，再把根因讲清楚。

## 本章先记住三句话

- `v0` 的价值是暴露问题，不是解决问题。
- “先校验库存，再扣减库存”在并发下不是原子操作。
- `AtomicInteger` 只能保证单次变量操作线程安全，不能保证整条业务流程正确。

## 章节信息

- 难度：入门
- 预计耗时：30 到 45 分钟
- 完成标准：能复现并解释“超卖 + 重复下单”

## 学习目标

完成本章后，你应该可以：

- 用实验结果证明 v0 会出现超卖和重复下单。
- 解释竞态窗口为什么会出现，以及出现在哪一步。
- 清楚说出 v1 第一优先需要补的两类保障能力。

## 开章前 5 分钟

如果并发术语还不熟，先看：

1. [前置知识快读](../fundamentals/prerequisites.md)
2. [核心概念讲解](../fundamentals/core-concepts.md)

## 3 分钟启动检查

1. 启动中间件（Docker）：

```bash
docker compose -f deploy/docker-compose.yml up -d
```

2. 在 IDEA 运行应用：

- 打开 `seckill-app/src/main/java/com/seckill/lab/SeckillLabApplication.java`
- 点击运行 `SeckillLabApplication`

3. 验证当前阶段：

```bash
curl "http://localhost:8080/api/stages/current"
```

若返回 `code=v0`，说明可进入实验。

## v0 故意保留的风险点

- 库存在内存中（`AtomicInteger`），不具备数据库级约束能力。
- 下单流程是“两步操作”：先读库存，再扣库存。
- 没有一人一单唯一约束。
- 没有请求幂等/消费去重机制。

对应代码可先快速浏览：`seckill-app/src/main/java/com/seckill/lab/stage/v0/service/V0NaiveSeckillService.java`。

## 动手实验（标准流程）

### 第 1 步：重置库存

```bash
curl -X POST "http://localhost:8080/api/v0/activities/1001/reset?stock=50"
```

预期：`success=true`，且 `remainingStock=50`。

### 第 2 步：发送突发流量

```bash
bash scripts/v0-burst.sh 1001 300 60
```

说明：`300` 代表总请求数，`60` 代表并发度。

### 第 3 步：查看快照

```bash
curl "http://localhost:8080/api/v0/activities/1001/snapshot"
```

重点关注：

- `oversoldUnits > 0`：发生超卖。
- `duplicateUserCount > 0`：发生重复下单。
- `remainingStock < 0`：库存被扣成负数。

## 观察记录模板（建议直接复制）

| 轮次 | stock | requests | concurrency | oversoldUnits | duplicateUserCount | remainingStock | 结论 |
|---|---:|---:|---:|---:|---:|---:|---|
| 第 1 轮 | 50 | 300 | 60 |  |  |  |  |
| 第 2 轮 | 50 | 1000 | 200 |  |  |  |  |

这张表后面可以直接和 `v1` 做前后对比。

## 根因拆解（时间线）

按下面顺序理解竞态：

1. 请求 A 读到库存 = 1
2. 请求 B 读到库存 = 1
3. 请求 A 扣减后库存 = 0
4. 请求 B 继续扣减后库存 = -1

两个请求都基于“旧库存值”通过了校验，最终出现超卖。

## 为什么 `AtomicInteger` 还会出错

- `get()` 和 `decrementAndGet()` 各自是线程安全的。
- 但“先判断再扣减”是跨两步的业务流程，不具备整体原子性。
- 业务正确性需要“流程原子化 + 约束兜底”，而不是只看单个变量操作。

## 常见误区

- “库存不为负就没事”：错误，重复下单同样是业务错误。
- “压测一次没复现就代表安全”：错误，并发问题天然有概率性。
- “计数器线程安全 = 下单链路正确”：错误，流程一致性是另一层问题。

## 练习任务

1. 加压测试并对比：

```bash
bash scripts/v0-burst.sh 1001 1000 200
```

2. 强制复现同一用户重复下单：

```bash
seq 1 30 | xargs -I{} -P 30 curl -s -X POST "http://localhost:8080/api/v0/activities/1001/attempt" -H "Content-Type: application/json" -d '{"userId":"u-fixed"}'
```

3. 用 3 句话写总结：

- 现象：你观察到了什么错误结果
- 根因：为什么会发生
- 改进：v1 最先要补哪两项保障

## 过章检查清单

- 你能稳定复现 `oversoldUnits > 0` 或 `duplicateUserCount > 0`。
- 你能用“时间线”解释竞态窗口。
- 你能清楚说出 v1 的两项优先能力：一人一单约束 + 库存正确扣减。

## 下一章预告

`v1` 会加入数据库正确性兜底：

- 一人一单唯一约束
- 库存乐观锁扣减
