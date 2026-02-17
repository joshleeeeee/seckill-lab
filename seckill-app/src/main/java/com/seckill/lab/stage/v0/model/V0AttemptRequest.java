package com.seckill.lab.stage.v0.model;

import jakarta.validation.constraints.NotBlank;

/**
 * v0 下单请求体。
 *
 * @param userId 用户标识，用于下单与重复下单统计
 */
public record V0AttemptRequest(@NotBlank(message = "userId must not be blank") String userId) {
}
