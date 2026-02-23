# v1 - DB 正确性兜底

如果说 `v0` 是把并发问题故意放大给你看，那么 `v1` 的任务就很明确：先让系统“不会错”，再谈“跑得更快”。秒杀场景里最危险的两件事，一件是超卖，一件是同一用户重复下单。`v1` 就是围绕这两件事，把正确性底线真正落到数据库里。

读完这一章，你应该能把这条逻辑讲清楚：为什么应用层的 `if` 判断并不可靠，为什么数据库约束才是最终裁判，以及乐观锁如何在并发下守住库存。

## 开始前，先把实验舞台搭起来

先启动中间件，让 MySQL、Redis、RabbitMQ 都可用：

```bash
# 启动实验依赖：MySQL / Redis / RabbitMQ
docker compose -f deploy/docker-compose.yml up -d
```

然后在 IDEA 启动应用，把 `Active profiles` 切到 `v1`。应用起来之后，用下面这个接口确认阶段是否正确：

```bash
# 验证当前激活阶段是否为 v1
curl "http://localhost:8080/api/stages/current"
```

如果返回里看到 `code=v1`，说明你已经站在这一章该站的位置上了。

## 先认路：v1 关键改动都在哪

为了阅读顺畅，先记住四个文件就够了。接口入口在 `seckill-app/src/main/java/com/seckill/lab/stage/v1/controller/V1SeckillController.java`，核心流程在 `seckill-app/src/main/java/com/seckill/lab/stage/v1/service/V1DbSeckillService.java`，库存乐观锁 SQL 在 `seckill-app/src/main/java/com/seckill/lab/stage/v1/mapper/V1ActivityMapper.java`，而一人一单的最终约束在 `seckill-app/src/main/resources/db/migration/V1__init_schema.sql`。

你可以先把注意力放在 `seckill_order` 的唯一索引 `(activity_id, user_id)` 上。它是并发重复下单场景里的最后裁决点，也是 v1 相比 v0 最关键的一道“硬约束”。

## 核心代码讲解：v1 为什么真的更稳

读 v1 这章时，可以把自己代入成一个正在下单的请求。它从控制器进入 `V1DbSeckillService#attempt`，然后一路走向数据库。在这个过程中，真正兜底正确性的不是某个 `if`，而是两条数据库规则：一条负责“不能超卖”，另一条负责“不能一人多单”。理解了这两条规则，再回头看业务代码，就不会觉得它只是“多写了几行判断”。

先看一人一单。这个约束最终写在 `seckill_order` 的唯一索引上：

```sql
CREATE TABLE IF NOT EXISTS seckill_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    ...,
    -- 一人一单的最终裁决：同活动同用户只能成功一次
    UNIQUE KEY uk_activity_user (activity_id, user_id)
);
```

这条索引的意思很直接：同一个 `activity_id` 和 `user_id` 组合，在表里最多只能出现一次。应用层当然也会先查一次“这个用户下过单没有”，但那只是减少无效写入的优化，不是最后裁判。并发情况下，两个请求可能几乎同时查到“还没下过单”，都继续往下走，最终还是要靠这条唯一索引决定谁能写入、谁必须失败。

再看库存为什么不会扣穿。关键不在“先读库存再减一”，而在下面这条条件更新 SQL：

```sql
UPDATE seckill_activity
SET available_stock = available_stock - 1,
    version = version + 1
WHERE id = #{activityId}
  AND status = 1
  AND available_stock > 0               -- 库存必须为正，防止扣成负数
  AND version = #{expectedVersion}      -- 版本必须匹配，防并发覆盖
```

它把“判断库存是否可扣”和“真正执行扣减”合并在同一条 SQL 里完成。`available_stock > 0` 确保库存不会被减成负数，`version = expectedVersion` 负责识别并发冲突。你可以想象两个请求同时读到 `version=10`，第一个请求成功后会把版本改成 `11`，第二个请求再执行时条件已经不成立，更新行数是 `0`。这就意味着第二个请求并没有真的扣到库存，自然也就谈不上超卖。

最后看 `attempt` 方法里最容易忽略、却非常关键的一段：

```java
int updated = activityMapper.deductStockWithVersion(activityId, version); // 原子扣库存
if (updated == 0) {
    continue; // 并发冲突，进入下一轮重试
}

try {
    orderMapper.insert(order); // 可能命中 uk_activity_user 唯一键
    return new V1AttemptResult(true, "OK", orderNo, remainingAfter);
} catch (DuplicateKeyException ex) {
    activityMapper.restoreOneStock(activityId); // 插单失败后回补库存，避免少卖一件
    return fail("DUPLICATE_ORDER", availableStock);
}
```

这里体现的是“先扣库存，再落订单”的真实代价：如果扣库存成功了，但插入订单时命中唯一键冲突，那这次请求其实没有形成有效订单，库存就必须回补一件。`restoreOneStock` 做的就是这个补偿动作。没有这一步，就会出现“用户没下成单，但库存已经少了”的数据不一致问题。也正因为这个窗口存在，v1 才会同时保留“应用层预检查 + 数据库唯一索引兜底”这套双保险，而不是只依赖其中之一。

你会在代码里看到 `MAX_OPTIMISTIC_RETRY = 3`。它不是为了一定成功，而是给瞬时冲突一个小的缓冲区：更新行数为 `0` 时允许再试几次。这样在中等冲突下成功率更好，但冲突特别高时，重试本身也会增加数据库压力。这正是 v1 的边界：正确性底线已经建立，但吞吐上限仍然受数据库写能力约束，这也是后续 v2 要把热点库存前置到 Redis 的原因。

如果你跑一遍 `bash scripts/burst.sh v1 1001 300 60`，再看 `snapshot` 里 `oversoldUnits` 和 `duplicateUserCount` 都是 `0`，就可以把实验结果和上面的实现一一对应起来：前者来自乐观锁条件扣减，后者来自唯一索引最终裁决。

## 动手实验：把结论跑成事实

理解完代码，最有说服力的方式就是亲手跑一遍。先把活动和库存重置到一个干净状态：

```bash
# 先把活动和库存重置到干净起点
curl -X POST "http://localhost:8080/api/v1/activities/1001/reset?stock=50"
```

接着直接发起并发压测：

```bash
# 300 次请求、60 并发，快速观察 v1 在压力下的行为
bash scripts/burst.sh v1 1001 300 60
```

这个脚本会自动完成重置、并发请求，并先输出中文延迟统计（吞吐量、延迟 P50/P95/P99）再输出快照。如果你想再复查一次，也可以手动查快照：

```bash
# 复查关键指标：oversoldUnits / duplicateUserCount
curl "http://localhost:8080/api/v1/activities/1001/snapshot"
```

这时候请盯住两个字段：`oversoldUnits` 和 `duplicateUserCount`。在 v1 的目标模型下，它们都应该是 `0`。

## 再做一个“同一用户并发”小实验

上面的压测默认是随机用户，如果你想更直观地验证一人一单，可以把请求都打到同一个 `userId` 上：

```bash
# 同一用户并发冲击，验证数据库唯一索引是否生效
seq 1 30 | xargs -I{} -P 30 curl -s -X POST "http://localhost:8080/api/v1/activities/1001/attempt" -H "Content-Type: application/json" -d '{"userId":"u-fixed"}'
```

在正常情况下，你会看到只有一次 `success=true`，其余请求返回 `DUPLICATE_ORDER`。这条现象和前面讲的唯一索引机制是完全对得上的。

## 把实验结果和实现真正对应起来

如果你在这里看到 `oversoldUnits=0`，它背后对应的是“带版本号的条件更新”确实挡住了并发扣穿；如果你看到 `duplicateUserCount=0`，它背后对应的是“唯一索引最终裁决”确实兜住了重复下单。换句话说，v1 比 v0 更稳，不是因为判断写得更多，而是因为把关键规则放到了数据库原子操作和约束里。

## v1 的边界，以及 v2 为什么会出现

当然，v1 不是终点。它把正确性立住了，但热点库存仍在数据库，冲突高时重试会放大 DB 压力，吞吐上限仍然受制于数据库写能力。所以接下来的 v2 才会顺理成章地登场：把热点库存前置到 Redis，用 Lua 脚本把校验和扣减收敛成原子步骤，在保持正确性的同时继续提升抗压能力（见 [v2 - Redis 热点库存](./v2-redis-stock.md)）。
