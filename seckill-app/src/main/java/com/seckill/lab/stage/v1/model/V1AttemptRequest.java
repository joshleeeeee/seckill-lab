package com.seckill.lab.stage.v1.model;

import jakarta.validation.constraints.NotBlank;

/**
 * v1 下单请求体。
 *
 * @param userId 用户标识
 */
public record V1AttemptRequest(@NotBlank(message = "userId must not be blank") String userId) {
}
