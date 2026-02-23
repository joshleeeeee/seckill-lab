package com.seckill.lab.stage.v2.controller;

import com.seckill.lab.common.ApiResponse;
import com.seckill.lab.stage.v2.model.V2AttemptRequest;
import com.seckill.lab.stage.v2.model.V2AttemptResult;
import com.seckill.lab.stage.v2.model.V2Snapshot;
import com.seckill.lab.stage.v2.service.V2RedisSeckillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 阶段秒杀接口。
 *
 * <p>该阶段把热点库存前置到 Redis，并用 Lua 脚本做原子扣减。
 */
@Validated
@RestController
@Profile("v2")
@RequestMapping("/api/v2/activities")
public class V2SeckillController {

    private final V2RedisSeckillService seckillService;

    public V2SeckillController(V2RedisSeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /**
     * 重置活动库存与订单数据，并预热 Redis 库存。
     */
    @PostMapping("/{activityId}/reset")
    public ApiResponse<V2Snapshot> reset(
            @PathVariable long activityId,
            @RequestParam(name = "stock", defaultValue = "50") @Min(1) @Max(500000) int stock
    ) {
        return ApiResponse.ok("Activity reset", seckillService.reset(activityId, stock));
    }

    /**
     * 执行一次 v2 下单尝试。
     */
    @PostMapping("/{activityId}/attempt")
    public ApiResponse<V2AttemptResult> attempt(
            @PathVariable long activityId,
            @Valid @RequestBody V2AttemptRequest request
    ) {
        return ApiResponse.ok(seckillService.attempt(activityId, request.userId()));
    }

    /**
     * 查询活动快照（库存优先读取 Redis）。
     */
    @GetMapping("/{activityId}/snapshot")
    public ApiResponse<V2Snapshot> snapshot(@PathVariable long activityId) {
        return ApiResponse.ok(seckillService.snapshot(activityId));
    }
}
