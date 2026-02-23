package com.seckill.lab.stage.v2.model;

import java.util.List;

/**
 * v2 活动快照。
 *
 * @param activityId 活动标识
 * @param totalStock 初始库存
 * @param availableStock 当前可用库存（优先读取 Redis）
 * @param totalOrders 已创建订单总数
 * @param oversoldUnits 超卖数量（v2 预期为 0）
 * @param duplicateUserCount 重复下单用户数（v2 预期为 0）
 * @param recentOrders 最近 10 条订单轨迹
 */
public record V2Snapshot(
        long activityId,
        int totalStock,
        int availableStock,
        long totalOrders,
        int oversoldUnits,
        long duplicateUserCount,
        List<String> recentOrders
) {
}
