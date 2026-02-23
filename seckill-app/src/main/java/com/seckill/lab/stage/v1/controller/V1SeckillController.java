package com.seckill.lab.stage.v1.controller;

import com.seckill.lab.common.ApiResponse;
import com.seckill.lab.stage.v1.model.V1AttemptRequest;
import com.seckill.lab.stage.v1.model.V1AttemptResult;
import com.seckill.lab.stage.v1.model.V1Snapshot;
import com.seckill.lab.stage.v1.service.V1DbSeckillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * v1 阶段秒杀接口。
 *
 * <p>该阶段基于数据库建立正确性底线：一人一单唯一约束 + 库存乐观锁扣减。
 */
@Validated
@RestController
@Profile("v1")
@RequestMapping("/api/v1/activities")
public class V1SeckillController {

    private final V1DbSeckillService seckillService;

    public V1SeckillController(V1DbSeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /**
     * 重置活动库存与订单数据，便于重复实验。
     */
    @PostMapping("/{activityId}/reset")
    public ApiResponse<V1Snapshot> reset(
            @PathVariable long activityId,
            @RequestParam(name = "stock", defaultValue = "50") @Min(1) @Max(500000) int stock
    ) {
        return ApiResponse.ok("Activity reset", seckillService.reset(activityId, stock));
    }

    /**
     * 执行一次 v1 下单尝试。
     */
    @PostMapping("/{activityId}/attempt")
    public ApiResponse<V1AttemptResult> attempt(
            @PathVariable long activityId,
            @Valid @RequestBody V1AttemptRequest request
    ) {
        return ApiResponse.ok(seckillService.attempt(activityId, request.userId()));
    }

    /**
     * 查询活动在数据库中的实时快照。
     */
    @GetMapping("/{activityId}/snapshot")
    public ApiResponse<V1Snapshot> snapshot(@PathVariable long activityId) {
        return ApiResponse.ok(seckillService.snapshot(activityId));
    }
}
