package com.seckill.lab.stage.v2.model;

import jakarta.validation.constraints.NotBlank;

/**
 * v2 下单请求体。
 *
 * @param userId 用户标识
 */
public record V2AttemptRequest(@NotBlank(message = "userId must not be blank") String userId) {
}
