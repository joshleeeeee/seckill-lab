# v2 - Redis 热点库存：把最拥堵的一段链路搬到快车道

`v1` 已经把“正确性底线”立住了：不超卖、不重复下单。但它还有一个绕不开的现实问题：高并发下最热的库存更新仍压在数据库上。你会看到冲突重试变多、请求时延抬升、数据库写争用越来越重。

`v2` 的任务不是再发明一套新规则，而是把最拥堵的一段路径改道：把库存扣减前置到 Redis，并用 Lua 把“库存校验 + 库存扣减”合成一个原子步骤。这样做的价值不是“更炫”，而是把热点写竞争从 MySQL 迁到 Redis 的内存原子执行路径上。

## 开始前准备：先确认你站在 v2

先启动中间件：

```bash
# 启动实验依赖：MySQL / Redis / RabbitMQ
docker compose -f deploy/docker-compose.yml up -d
```

然后在 IDEA 启动应用，并把 `Active profiles` 设为 `v2`。启动后确认当前阶段：

```bash
# 验证当前阶段是否已切到 v2
curl "http://localhost:8080/api/stages/current"
```

看到返回 `code=v2`，说明你已经进入本章实验语境。

## 先立三条不变量：不先立规则，讨论性能会跑偏

读 v2 之前，先把它要守住的三条底线记清楚。第一条是**不超卖**：`totalOrders <= totalStock`。第二条是**一人一单**：同一 `(activity_id, user_id)` 只能落一条订单。第三条是**库存守恒（近实时）**：Redis 库存与 DB 订单数应可互相解释，出现偏差时必须可回补、可收敛。

v2 的所有实现细节，最终都在服务这三条不变量，而不是单纯“把 Redis 用上”。

## 结构变化：v2 到底把什么挪走了

一句话总结：`v1` 在 DB 内完成库存争用裁决，`v2` 把“热点库存裁决”前置到 Redis，DB 继续做最终业务约束裁判。

关键代码位置：

- 接口入口：`seckill-app/src/main/java/com/seckill/lab/stage/v2/controller/V2SeckillController.java`
- 核心流程：`seckill-app/src/main/java/com/seckill/lab/stage/v2/service/V2RedisSeckillService.java`
- Lua 脚本：`seckill-app/src/main/resources/lua/v2-deduct-stock.lua`
- 一人一单唯一约束：`seckill-app/src/main/resources/db/migration/V1__init_schema.sql`

这里有个很重要的设计取舍：v2 并没有删除 DB 唯一索引，而是让它继续当“最终裁判”。因为 Redis 负责的是热点库存原子扣减，业务唯一性仍要靠 DB 约束兜底。

## 核心实现 1：Lua 为什么能扛住并发扣减

先看脚本：

```lua
local key = KEYS[1]

if redis.call('EXISTS', key) == 0 then
    return -2 -- key 不存在：让服务层按 DB 结果回源预热
end

local stock = tonumber(redis.call('GET', key))
if not stock then
    return -3 -- 脏值保护：不是数字就拒绝继续扣减
end

if stock <= 0 then
    return -1 -- 售罄：库存到 0 后直接拒绝
end

return redis.call('DECR', key) -- 原子扣减并返回剩余库存
```

这段脚本真正关键的点不是 `DECR`，而是执行语义：Lua 在 Redis 中按事件循环串行执行，脚本执行期间不会插入其它命令。所以“判断 + 扣减”是在同一个原子执行单元里完成，天然避免 TOCTOU（先检查后使用）窗口。

但也要认清边界：Lua 原子只覆盖 Redis 内部，不会自动跨到 MySQL 事务。也正因为这个边界，服务层才必须设计补偿路径。

## 核心实现 2：服务层如何处理跨存储一致性窗口

`V2RedisSeckillService#attempt` 的关键路径是：活动校验 -> 重复下单预检查 -> Lua 扣库存 -> DB 落订单 -> 失败补偿。关键片段如下：

```java
Long deductedRemaining = deductStockFromRedis(activityId, activity); // Redis Lua 原子扣减
if (deductedRemaining == LUA_RESULT_SOLD_OUT) {
    return fail("SOLD_OUT", 0); // 库存不足直接拒绝
}

try {
    orderMapper.insert(order); // 订单最终落库，受唯一索引兜底
    return new V2AttemptResult(true, "OK", orderNo, deductedRemaining.intValue());
} catch (DuplicateKeyException ex) {
    Integer restoredStock = restoreOneStock(activityId); // 唯一键冲突后回补 Redis 库存
    return fail("DUPLICATE_ORDER", restoredStock);
} catch (RuntimeException ex) {
    Integer restoredStock = restoreOneStock(activityId); // 落库异常时也回补，避免库存永久少扣
    throw ex;
}
```

这里最容易被忽略的，是最后这个 `RuntimeException` 分支：它不是“多写一层保险”，而是为了覆盖“Redis 已扣减、DB 未成功落单”的一致性窗口。如果不回补，这类请求会造成库存丢失（少卖），长期会把系统拖入脏状态。

## 一个容易误解的点：v2 的库存主视图已经迁到 Redis

在 v2 里，活动表的 `available_stock` 不是实时热点来源；快照优先读 Redis key（`seckill:v2:stock:{activityId}`），当 key 缺失或脏值时才回退到 `totalStock - totalOrders` 的估算值。这个设计说明了 v2 的核心思想：**DB 负责事实归档（订单），Redis 负责热点库存实时裁决**。

这也解释了为什么服务里有 `LUA_RESULT_KEY_MISSING` 分支：当 Redis key 丢失时，系统会按当前 DB 订单数回源计算可用库存并重新预热，再继续执行 Lua 扣减。

## 动手实验：先验证正确性，再看承压变化

先重置活动：

```bash
# 重置活动并同步预热 Redis 库存
curl -X POST "http://localhost:8080/api/v2/activities/1001/reset?stock=50"
```

再打突发流量：

```bash
# 300 次请求、60 并发，观察 Redis 热点库存路径表现
bash scripts/burst.sh v2 1001 300 60
```

脚本会先输出一段中文延迟统计（吞吐量、延迟 P50/P95/P99），再输出本轮快照。

然后手动看快照：

```bash
# 复查核心结果：库存、超卖、重复下单
curl "http://localhost:8080/api/v2/activities/1001/snapshot"
```

如果 `oversoldUnits=0` 且 `duplicateUserCount=0`，说明 v2 在当前压力下守住了正确性底线。

## 再做一组同用户并发：确认唯一约束还在生效

```bash
# 固定同一 userId 并发冲击，验证 DB 唯一约束兜底
seq 1 30 | xargs -I{} -P 30 curl -s -X POST "http://localhost:8080/api/v2/activities/1001/attempt" -H "Content-Type: application/json" -d '{"userId":"u-fixed"}'
```

正常情况下，只有一次成功，其余返回 `DUPLICATE_ORDER`。这说明 v2 虽然把库存路径前置到了 Redis，但业务唯一性仍由 DB 约束稳定兜底。

## 进阶观测：用“Redis 库存 + DB 订单”做一次现场对账

除了看接口快照，你还可以直接看底层数据面：

```bash
# 查看 Redis 热点库存 key（实时库存视图）
docker exec seckill-redis redis-cli GET "seckill:v2:stock:1001"
```

```bash
# 查看 DB 成交订单总数（最终事实）
docker exec seckill-mysql mysql -useckill -pseckill -D seckill_lab -e "SELECT COUNT(1) AS total_orders FROM seckill_order WHERE activity_id = 1001;"
```

理论上应满足：`redis_stock + total_orders ~= total_stock`（允许瞬时波动，但应可收敛）。如果你发现长期不收敛，就意味着补偿或回源策略存在缺口，这是后续演进必须补齐的观察点。

## 局限与下一步：v2 解决热点争用，不解决异步削峰

v2 已经把库存热点冲突从 DB 挪到了 Redis，并且保持了 v1 的正确性约束。但订单写库仍在同步链路里，峰值时延仍受 DB 写能力限制。这就是 v3 要接手的问题：引入 MQ，把写库从“请求直写”改成“排队异步写”，把洪峰继续摊平。

你可以把 v2 理解为一个关键转场：先解决“库存争用在哪里裁决”，再解决“成交写入如何削峰”。前者稳了，后者才有空间做深。
