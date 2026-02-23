package com.seckill.lab.stage.v1.model;

/**
 * v1 单次下单尝试结果。
 *
 * @param success        是否受理成功
 * @param note           结果标记，如 OK、SOLD_OUT、DUPLICATE_ORDER
 * @param orderNo        成功时生成的业务订单号
 * @param remainingStock 本次处理后估算的剩余库存
 */
public record V1AttemptResult(boolean success, String note, String orderNo, Integer remainingStock) {
}
