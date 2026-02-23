package com.seckill.lab.stage.v2.service;

import com.seckill.lab.stage.v2.entity.V2ActivityEntity;
import com.seckill.lab.stage.v2.entity.V2OrderEntity;
import com.seckill.lab.stage.v2.mapper.V2ActivityMapper;
import com.seckill.lab.stage.v2.mapper.V2OrderMapper;
import com.seckill.lab.stage.v2.model.V2AttemptResult;
import com.seckill.lab.stage.v2.model.V2Snapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * v2 阶段 Redis 库存版秒杀服务。
 *
 * <p>核心目标：将库存热点路径从 MySQL 前置到 Redis，并通过 Lua 原子扣减防止超卖。
 */
@Service
@Profile("v2")
@Slf4j
public class V2RedisSeckillService {

    private static final int DEFAULT_STOCK = 50;
    private static final long LUA_RESULT_SOLD_OUT = -1L;
    private static final long LUA_RESULT_KEY_MISSING = -2L;
    private static final long LUA_RESULT_STOCK_INVALID = -3L;
    private static final String STOCK_KEY_PREFIX = "seckill:v2:stock:";
    private static final RedisScript<Long> DEDUCT_STOCK_SCRIPT = loadDeductStockScript();

    private final V2ActivityMapper activityMapper;

    private final V2OrderMapper orderMapper;

    private final StringRedisTemplate redisTemplate;

    public V2RedisSeckillService(
            V2ActivityMapper activityMapper,
            V2OrderMapper orderMapper,
            StringRedisTemplate redisTemplate
    ) {
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 重置活动库存与订单数据，并同步重置 Redis 库存。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public V2Snapshot reset(long activityId, int stock) {
        // 先清理旧订单，保证本轮实验不会受上轮数据污染。
        orderMapper.deleteByActivityId(activityId);

        LocalDateTime now = LocalDateTime.now();
        // 通过 upsert 统一“首次创建”和“重复重置”两种路径。
        activityMapper.upsertActivity(
                activityId,
                "Activity-" + activityId,
                now.minusMinutes(5),
                now.plusDays(1),
                stock,
                stock
        );

        // Redis 库存作为 v2 的热点主视图，重置时同步预热。
        redisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(stock));
        log.info("v2 reset activity, activityId={}, stock={}", activityId, stock);
        return snapshot(activityId);
    }

    /**
     * 执行一次下单尝试。
     *
     * <p>流程：活动校验 -> 一人一单预检查 -> Redis Lua 原子扣减 -> 落订单。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public V2AttemptResult attempt(long activityId, String userId) {
        V2ActivityEntity activity = activityMapper.selectById(activityId);
        // 活动不存在或不可用，直接返回。
        if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
            return fail("ACTIVITY_NOT_FOUND", null);
        }

        // 应用层先做一次重复下单预检查，减少无效 Redis/DB 写入。
        if (orderMapper.countByActivityAndUser(activityId, userId) > 0) {
            Integer availableStock = activity.getAvailableStock() == null ? null : Math.max(0, activity.getAvailableStock());
            return fail("DUPLICATE_ORDER", availableStock);
        }

        // 先走 Redis Lua 原子扣减，快速裁决库存热点路径。
        Long deductedRemaining = deductStockFromRedis(activityId, activity);
        if (deductedRemaining == null) {
            return fail("REDIS_EXECUTION_ERROR", null);
        }
        if (deductedRemaining == LUA_RESULT_SOLD_OUT) {
            return fail("SOLD_OUT", 0);
        }
        if (deductedRemaining == LUA_RESULT_STOCK_INVALID) {
            return fail("STOCK_DATA_INVALID", null);
        }
        if (deductedRemaining < 0) {
            return fail("REDIS_STOCK_ERROR", null);
        }

        String orderNo = orderNo(activityId);
        try {
            // Redis 扣减成功后再落订单，DB 唯一索引负责最终一人一单裁决。
            V2OrderEntity order = new V2OrderEntity();
            order.setOrderNo(orderNo);
            order.setActivityId(activityId);
            order.setUserId(userId);
            order.setOrderStatus(0);
            orderMapper.insert(order);

            return new V2AttemptResult(true, "OK", orderNo, deductedRemaining.intValue());
        } catch (DuplicateKeyException ex) {
            // 命中唯一键代表最终裁决为重复下单，需回补 Redis 库存。
            Integer restoredStock = restoreOneStock(activityId);
            log.info("v2 duplicate order rejected by unique constraint, activityId={}, userId={}", activityId, userId);
            return fail("DUPLICATE_ORDER", restoredStock);
        } catch (RuntimeException ex) {
            // 订单落库异常同样回补库存，避免出现“库存少了但订单没落库”。
            Integer restoredStock = restoreOneStock(activityId);
            log.error(
                    "v2 order create failed after redis deduction, activityId={}, userId={}, restoredStock={}",
                    activityId,
                    userId,
                    restoredStock,
                    ex
            );
            throw ex;
        }
    }

    /**
     * 读取活动快照，用于观察本阶段正确性指标。
     */
    public V2Snapshot snapshot(long activityId) {
        V2ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            // 活动不存在时返回空快照，避免上层额外判空。
            return new V2Snapshot(activityId, 0, 0, 0, 0, 0, List.of());
        }

        long totalOrders = orderMapper.countByActivityId(activityId);
        long duplicateUserCount = orderMapper.countDuplicateUsers(activityId);
        int totalStock = activity.getTotalStock() == null ? 0 : activity.getTotalStock();
        // Redis key 缺失或脏值时，使用 DB 估算库存作为回退值。
        int fallbackStock = Math.max(0, totalStock - (int) totalOrders);
        int availableStock = resolveAvailableStock(activityId, fallbackStock);
        int oversoldUnits = Math.max(0, (int) (totalOrders - totalStock));
        List<String> recentOrders = orderMapper.selectRecentOrderTraces(activityId);

        return new V2Snapshot(
                activityId,
                totalStock,
                availableStock,
                totalOrders,
                oversoldUnits,
                duplicateUserCount,
                recentOrders
        );
    }

    private Long deductStockFromRedis(long activityId, V2ActivityEntity activity) {
        String key = stockKey(activityId);
        Long result = redisTemplate.execute(DEDUCT_STOCK_SCRIPT, List.of(key));
        if (result == null) {
            // null 表示脚本执行异常或连接问题。
            return null;
        }

        if (result == LUA_RESULT_KEY_MISSING) {
            // Redis 库存 key 丢失时按 DB 订单数回源估算，再补 key 后重试一次脚本。
            long totalOrders = orderMapper.countByActivityId(activityId);
            int totalStock = activity.getTotalStock() == null ? DEFAULT_STOCK : activity.getTotalStock();
            int fallbackStock = Math.max(0, totalStock - (int) totalOrders);
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(fallbackStock));
            result = redisTemplate.execute(DEDUCT_STOCK_SCRIPT, List.of(key));
        }
        return result;
    }

    private int resolveAvailableStock(long activityId, int fallbackStock) {
        String value = redisTemplate.opsForValue().get(stockKey(activityId));
        if (value == null) {
            // key 不存在时直接回退到 DB 估算值。
            return fallbackStock;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            // Redis 出现脏数据时打印告警并回退，保证快照接口可用。
            log.warn("v2 redis stock parse failed, activityId={}, value={}", activityId, value);
            return fallbackStock;
        }
    }

    private Integer restoreOneStock(long activityId) {
        // 回补库存使用 INCR，保持 Redis 端原子加一。
        Long restored = redisTemplate.opsForValue().increment(stockKey(activityId));
        return restored == null ? null : restored.intValue();
    }

    private String stockKey(long activityId) {
        return STOCK_KEY_PREFIX + activityId;
    }

    private V2AttemptResult fail(String note, Integer remainingStock) {
        return new V2AttemptResult(false, note, null, remainingStock);
    }

    private String orderNo(long activityId) {
        return "RSO-" + activityId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static RedisScript<Long> loadDeductStockScript() {
        // 启动时预加载 Lua 脚本定义，运行期直接执行脚本对象。
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/v2-deduct-stock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
