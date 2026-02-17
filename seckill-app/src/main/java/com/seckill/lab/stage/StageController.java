package com.seckill.lab.stage;

import com.seckill.lab.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stages")
@Slf4j
public class StageController {

    private final StageCatalog stageCatalog;

    public StageController(StageCatalog stageCatalog) {
        this.stageCatalog = stageCatalog;
    }

    @GetMapping
    public ApiResponse<List<StageInfo>> allStages() {
        List<StageInfo> stages = stageCatalog.all();
        log.debug("Returned stage list, count={}", stages.size());
        return ApiResponse.ok(stages);
    }

    @GetMapping("/current")
    public ApiResponse<StageInfo> currentStage() {
        StageInfo currentStage = stageCatalog.current();
        log.debug("Returned current stage, code={}, status={}", currentStage.code(), currentStage.status());
        return ApiResponse.ok(currentStage);
    }
}
