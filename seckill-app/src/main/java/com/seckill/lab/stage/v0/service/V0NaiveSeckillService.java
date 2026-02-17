package com.seckill.lab.stage.v0.service;

import com.seckill.lab.stage.v0.model.V0AttemptResult;
import com.seckill.lab.stage.v0.model.V0Snapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

/**
 * v0 阶段的朴素秒杀实现。
 *
 * <p>该服务故意保留竞态窗口和重复下单行为，便于教学中稳定复现并发问题。
 */
@Service
@Slf4j
public class V0NaiveSeckillService {

    private static final int DEFAULT_STOCK = 50;
    private static final int RACE_WINDOW_MILLIS = 8;

    private final ConcurrentHashMap<Long, ActivityState> states = new ConcurrentHashMap<>();

    /**
     * 重置指定活动的运行状态和初始库存。
     */
    public V0Snapshot reset(long activityId, int stock) {
        states.put(activityId, new ActivityState(stock));
        log.info("v0 reset activity, activityId={}, stock={}", activityId, stock);
        return snapshot(activityId);
    }

    /**
     * 按“先校验再扣减”的非原子流程处理一次下单请求。
     */
    public V0AttemptResult attempt(long activityId, String userId) {
        ActivityState state = states.computeIfAbsent(activityId, ignored -> new ActivityState(DEFAULT_STOCK));

        // 第一步：先读取库存。
        int seenStock = state.remainingStock.get();
        if (seenStock <= 0) {
            log.debug("v0 attempt rejected, activityId={}, userId={}, reason=SOLD_OUT", activityId, userId);
            return new V0AttemptResult(false, "SOLD_OUT", null, state.remainingStock.get());
        }

        // 故意扩大竞态窗口，提升并发问题复现概率。
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(RACE_WINDOW_MILLIS));

        // 第二步：基于上面的旧值继续扣减（与校验不是一个原子操作）。
        int remainingAfter = state.remainingStock.decrementAndGet();
        String orderId = orderId(activityId);
        state.orders.add(new OrderRecord(orderId, userId, Instant.now()));

        String note = remainingAfter < 0 ? "OVERSELL_HAPPENED" : "OK";
        if (remainingAfter < 0) {
            log.warn(
                    "v0 oversell detected, activityId={}, userId={}, orderId={}, remainingAfter={}",
                    activityId,
                    userId,
                    orderId,
                    remainingAfter
            );
        }
        return new V0AttemptResult(true, note, orderId, remainingAfter);
    }

    /**
     * 生成用于实验分析的活动快照。
     */
    public V0Snapshot snapshot(long activityId) {
        ActivityState state = states.computeIfAbsent(activityId, ignored -> new ActivityState(DEFAULT_STOCK));
        List<OrderRecord> allOrders = new ArrayList<>(state.orders);

        // 统计每个用户的下单次数，用于识别重复下单。
        Map<String, Long> orderCountByUser = allOrders.stream()
                .collect(Collectors.groupingBy(OrderRecord::userId, Collectors.counting()));

        long duplicateUserCount = orderCountByUser.values().stream()
                .filter(count -> count > 1)
                .count();

        int totalOrders = allOrders.size();
        // 超卖量 = 总订单数 - 初始库存（最小为 0）。
        int oversoldUnits = Math.max(0, totalOrders - state.initialStock);

        // 仅保留最近 10 条订单，便于接口观察。
        List<String> recentOrders = allOrders.stream()
                .sorted(Comparator.comparing(OrderRecord::createdAt).reversed())
                .limit(10)
                .map(order -> order.orderId() + ":" + order.userId())
                .toList();

        log.debug(
                "v0 snapshot built, activityId={}, initialStock={}, remainingStock={}, totalOrders={}, oversoldUnits={}, duplicateUserCount={}",
                activityId,
                state.initialStock,
                state.remainingStock.get(),
                totalOrders,
                oversoldUnits,
                duplicateUserCount
        );

        return new V0Snapshot(
                activityId,
                state.initialStock,
                state.remainingStock.get(),
                totalOrders,
                oversoldUnits,
                duplicateUserCount,
                recentOrders
        );
    }

    /**
     * 生成演示用的短订单号。
     */
    private String orderId(long activityId) {
        return "O-" + activityId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 单个活动的运行期可变状态。
     */
    private static final class ActivityState {
        private final int initialStock;
        private final AtomicInteger remainingStock;
        private final Queue<OrderRecord> orders;

        private ActivityState(int initialStock) {
            this.initialStock = initialStock;
            this.remainingStock = new AtomicInteger(initialStock);
            this.orders = new ConcurrentLinkedQueue<>();
        }
    }

    /**
     * 快照展示使用的订单记录。
     */
    private record OrderRecord(String orderId, String userId, Instant createdAt) {
    }
}
