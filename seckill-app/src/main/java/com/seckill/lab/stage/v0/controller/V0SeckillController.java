package com.seckill.lab.stage.v0.controller;

import com.seckill.lab.common.ApiResponse;
import com.seckill.lab.stage.v0.model.V0AttemptRequest;
import com.seckill.lab.stage.v0.model.V0AttemptResult;
import com.seckill.lab.stage.v0.model.V0Snapshot;
import com.seckill.lab.stage.v0.service.V0NaiveSeckillService;
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
 * v0 阶段秒杀接口。
 *
 * <p>该阶段故意保留朴素实现，用于复现超卖与重复下单问题。
 */
@Validated
@RestController
@Profile("v0")
@RequestMapping("/api/v0/activities")
public class V0SeckillController {

    private final V0NaiveSeckillService seckillService;

    public V0SeckillController(V0NaiveSeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /**
     * 将活动库存重置为可复现实验的基线值。
     */
    @PostMapping("/{activityId}/reset")
    public ApiResponse<V0Snapshot> reset(
            @PathVariable long activityId,
            @RequestParam(name = "stock", defaultValue = "50") @Min(1) @Max(500000) int stock
    ) {
        return ApiResponse.ok("Activity reset", seckillService.reset(activityId, stock));
    }

    /**
     * 执行一次 v0 下单尝试（故意保持非原子流程）。
     */
    @PostMapping("/{activityId}/attempt")
    public ApiResponse<V0AttemptResult> attempt(
            @PathVariable long activityId,
            @Valid @RequestBody V0AttemptRequest request
    ) {
        return ApiResponse.ok(seckillService.attempt(activityId, request.userId()));
    }

    /**
     * 查询活动当前运行快照。
     */
    @GetMapping("/{activityId}/snapshot")
    public ApiResponse<V0Snapshot> snapshot(@PathVariable long activityId) {
        return ApiResponse.ok(seckillService.snapshot(activityId));
    }
}
