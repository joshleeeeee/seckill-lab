package com.seckill.lab.stage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证当前阶段解析规则：优先 active profile，其次 default profile。
 */
class StageCatalogTest {

    @Test
    void resolvesCurrentStageFromActiveProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker", "v1");
        environment.setDefaultProfiles("v0");

        StageCatalog stageCatalog = new StageCatalog(environment);

        assertThat(stageCatalog.current().code()).isEqualTo("v1");
    }

    @Test
    void fallsBackToDefaultProfilesWhenActiveStageIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker");
        environment.setDefaultProfiles("v0");

        StageCatalog stageCatalog = new StageCatalog(environment);

        assertThat(stageCatalog.current().code()).isEqualTo("v0");
    }

    @Test
    void fallsBackToFirstStageWhenNoStageProfileIsConfigured() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker");
        environment.setDefaultProfiles("default");

        StageCatalog stageCatalog = new StageCatalog(environment);

        assertThat(stageCatalog.current().code()).isEqualTo("v0");
    }
}
