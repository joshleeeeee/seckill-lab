package com.seckill.lab.stage.v1.model;

import java.util.List;

/**
 * v1 活动快照。
 *
 * @param activityId         活动标识
 * @param totalStock         初始库存
 * @param availableStock     当前可用库存
 * @param version            当前乐观锁版本
 * @param totalOrders        已创建订单总数
 * @param oversoldUnits      超卖数量（v1 预期为 0）
 * @param duplicateUserCount 重复下单用户数（v1 预期为 0）
 * @param recentOrders       最近 10 条订单轨迹
 */
public record V1Snapshot(
        long activityId,
        int totalStock,
        int availableStock,
        int version,
        long totalOrders,
        int oversoldUnits,
        long duplicateUserCount,
        List<String> recentOrders
) {
}
