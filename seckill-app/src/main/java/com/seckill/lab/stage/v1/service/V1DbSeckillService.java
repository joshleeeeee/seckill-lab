package com.seckill.lab.stage.v1.service;

import com.seckill.lab.stage.v1.entity.V1ActivityEntity;
import com.seckill.lab.stage.v1.entity.V1OrderEntity;
import com.seckill.lab.stage.v1.mapper.V1ActivityMapper;
import com.seckill.lab.stage.v1.mapper.V1OrderMapper;
import com.seckill.lab.stage.v1.model.V1AttemptResult;
import com.seckill.lab.stage.v1.model.V1Snapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * v1 阶段数据库版秒杀服务。
 *
 * <p>核心目标：先建立“正确性优先”的基线能力。
 * 1) 通过数据库唯一约束兜底一人一单；
 * 2) 通过版本号乐观锁扣减库存，避免并发下超卖。
 */
@Service
@Profile("v1")
@Slf4j
public class V1DbSeckillService {

    private static final int MAX_OPTIMISTIC_RETRY = 3;

    private final V1ActivityMapper activityMapper;

    private final V1OrderMapper orderMapper;

    public V1DbSeckillService(V1ActivityMapper activityMapper, V1OrderMapper orderMapper) {
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 重置活动库存与订单数据，便于重复压测对比。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public V1Snapshot reset(long activityId, int stock) {
        // 先清理该活动历史订单，避免影响本轮实验统计。
        orderMapper.deleteByActivityId(activityId);

        LocalDateTime now = LocalDateTime.now();
        // 活动不存在则插入，存在则覆盖重置库存和版本号。
        activityMapper.upsertActivity(
                activityId,
                "Activity-" + activityId,
                now.minusMinutes(5),
                now.plusDays(1),
                stock,
                stock
        );

        log.info("v1 reset activity, activityId={}, stock={}", activityId, stock);
        return snapshot(activityId);
    }

    /**
     * 执行一次下单尝试。
     *
     * <p>流程：活动校验 -> 一人一单预检查 -> 乐观锁扣减库存 -> 落订单。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public V1AttemptResult attempt(long activityId, String userId) {
        // 乐观锁冲突时允许有限重试，避免瞬时冲突导致过多失败。
        for (int retry = 1; retry <= MAX_OPTIMISTIC_RETRY; retry++) {
            V1ActivityEntity activity = activityMapper.selectById(activityId);
            if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
                return fail("ACTIVITY_NOT_FOUND", null);
            }

            Integer availableStock = activity.getAvailableStock();
            if (availableStock == null || availableStock <= 0) {
                return fail("SOLD_OUT", 0);
            }

            Integer version = activity.getVersion();
            if (version == null) {
                return fail("ACTIVITY_DATA_INVALID", availableStock);
            }

            // 应用层先做一次重复下单预检查，减少无效写入。
            if (orderMapper.countByActivityAndUser(activityId, userId) > 0) {
                return fail("DUPLICATE_ORDER", availableStock);
            }

            // 带版本号更新库存，只有“版本匹配且库存>0”才会成功扣减。
            int updated = activityMapper.deductStockWithVersion(activityId, version);
            if (updated == 0) {
                log.debug(
                        "v1 stock optimistic-lock conflict, activityId={}, userId={}, retry={}",
                        activityId,
                        userId,
                        retry
                );
                continue;
            }

            String orderNo = orderNo(activityId);
            try {
                V1OrderEntity order = new V1OrderEntity();
                order.setOrderNo(orderNo);
                order.setActivityId(activityId);
                order.setUserId(userId);
                order.setOrderStatus(0);
                orderMapper.insert(order);

                int remainingAfter = Math.max(0, availableStock - 1);
                return new V1AttemptResult(true, "OK", orderNo, remainingAfter);
            } catch (DuplicateKeyException ex) {
                // 极端并发下仍可能撞上数据库唯一键，需把已扣库存回补回来。
                activityMapper.restoreOneStock(activityId);
                log.info("v1 duplicate order rejected by unique constraint, activityId={}, userId={}", activityId, userId);
                return fail("DUPLICATE_ORDER", availableStock);
            }
        }

        log.warn(
                "v1 stock update retried and exhausted, activityId={}, userId={}, maxRetry={}",
                activityId,
                userId,
                MAX_OPTIMISTIC_RETRY
        );
        return fail("STOCK_CONFLICT_RETRY_EXHAUSTED", null);
    }

    /**
     * 读取活动快照，用于观察本阶段正确性指标。
     */
    public V1Snapshot snapshot(long activityId) {
        V1ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return new V1Snapshot(activityId, 0, 0, 0, 0, 0, 0, List.of());
        }

        long totalOrders = orderMapper.countByActivityId(activityId);
        long duplicateUserCount = orderMapper.countDuplicateUsers(activityId);
        int totalStock = activity.getTotalStock() == null ? 0 : activity.getTotalStock();
        int availableStock = activity.getAvailableStock() == null ? 0 : activity.getAvailableStock();
        int version = activity.getVersion() == null ? 0 : activity.getVersion();
        int oversoldUnits = Math.max(0, (int) (totalOrders - totalStock));
        List<String> recentOrders = orderMapper.selectRecentOrderTraces(activityId);

        return new V1Snapshot(
                activityId,
                totalStock,
                availableStock,
                version,
                totalOrders,
                oversoldUnits,
                duplicateUserCount,
                recentOrders
        );
    }

    /**
     * 构造统一的失败返回体。
     */
    private V1AttemptResult fail(String note, Integer remainingStock) {
        return new V1AttemptResult(false, note, null, remainingStock);
    }

    /**
     * 生成演示用订单号，便于日志和快照快速识别。
     */
    private String orderNo(long activityId) {
        return "DBO-" + activityId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
