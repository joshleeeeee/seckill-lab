package com.seckill.lab.stage.v0.model;

import java.util.List;

/**
 * v0 活动运行快照。
 *
 * @param activityId 活动标识
 * @param initialStock 最近一次重置后的初始库存
 * @param remainingStock 当前内存库存
 * @param totalOrders 已受理订单总数
 * @param oversoldUnits 超卖数量
 * @param duplicateUserCount 下单次数大于 1 的用户数量
 * @param recentOrders 最近订单轨迹，格式为 "orderId:userId"
 */
public record V0Snapshot(
        long activityId,
        int initialStock,
        int remainingStock,
        int totalOrders,
        int oversoldUnits,
        long duplicateUserCount,
        List<String> recentOrders
) {
}
