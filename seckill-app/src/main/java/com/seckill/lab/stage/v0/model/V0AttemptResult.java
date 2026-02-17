package com.seckill.lab.stage.v0.model;

/**
 * v0 单次下单尝试结果。
 *
 * @param success 是否受理成功
 * @param note 结果标记，如 OK、SOLD_OUT、OVERSELL_HAPPENED
 * @param orderId 成功时生成的订单号
 * @param remainingStock 本次处理后的剩余库存
 */
public record V0AttemptResult(boolean success, String note, String orderId, int remainingStock) {
}
