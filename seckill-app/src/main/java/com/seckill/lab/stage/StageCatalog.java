package com.seckill.lab.stage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class StageCatalog {

    private final List<StageInfo> stages = List.of(
            new StageInfo("v0", "Naive Sync Order", "Reproduce oversell and duplicate orders", StageStatus.AVAILABLE),
            new StageInfo("v1", "DB Guard", "Add optimistic lock and one-user-one-order", StageStatus.PLANNED),
            new StageInfo("v2", "Redis Stock", "Move hot stock to Redis with Lua", StageStatus.PLANNED),
            new StageInfo("v3", "Async Order", "Use message queue to buffer spikes", StageStatus.PLANNED),
            new StageInfo("v4", "Idempotency", "Deduplicate retries and repeated messages", StageStatus.PLANNED),
            new StageInfo("v5", "Compensation", "Recover from timeout and partial failures", StageStatus.PLANNED),
            new StageInfo("v6", "Anti Bot", "Add abuse control and risk checks", StageStatus.PLANNED),
            new StageInfo("v7", "Observability", "Metrics, dashboards, and tracing", StageStatus.PLANNED),
            new StageInfo("v8", "Scale Out", "Optional split into microservices", StageStatus.PLANNED)
    );

    private final Set<String> stageCodes = stages.stream().map(StageInfo::code).collect(Collectors.toSet());

    private final Environment environment;

    public StageCatalog(Environment environment) {
        this.environment = environment;
        log.info("Stage catalog initialized, stageCount={}, stageCodes={}", stages.size(), stageCodes);
    }

    public List<StageInfo> all() {
        return stages;
    }

    public StageInfo current() {
        String currentStageCode = resolveCurrentStageCode();
        StageInfo currentStage = stages.stream()
                .filter(stage -> stage.code().equals(currentStageCode))
                .findFirst()
                .orElse(stages.get(0));
        log.debug(
                "Resolved current stage, code={}, activeProfiles={}, defaultProfiles={}",
                currentStage.code(),
                Arrays.toString(environment.getActiveProfiles()),
                Arrays.toString(environment.getDefaultProfiles())
        );
        return currentStage;
    }

    private String resolveCurrentStageCode() {
        for (String profile : environment.getActiveProfiles()) {
            if (stageCodes.contains(profile)) {
                log.debug("Stage resolved from active profile, profile={}", profile);
                return profile;
            }
        }

        for (String profile : environment.getDefaultProfiles()) {
            if (stageCodes.contains(profile)) {
                log.debug("Stage resolved from default profile, profile={}", profile);
                return profile;
            }
        }

        log.warn("No stage profile matched active/default profiles, fallbackStage={}", stages.get(0).code());
        return stages.get(0).code();
    }
}
