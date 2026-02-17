package com.seckill.lab.stage.v0.service;

import com.seckill.lab.stage.v0.model.V0Snapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 明确 v0 教学基线：同一用户允许重复下单。
 */
class V0NaiveSeckillServiceTest {

    private final V0NaiveSeckillService service = new V0NaiveSeckillService();

    @Test
    void allowsDuplicateOrdersForSameUser() {
        long activityId = 1001L;
        service.reset(activityId, 10);

        service.attempt(activityId, "u-1");
        service.attempt(activityId, "u-1");

        V0Snapshot snapshot = service.snapshot(activityId);
        assertThat(snapshot.totalOrders()).isEqualTo(2);
        assertThat(snapshot.duplicateUserCount()).isEqualTo(1);
    }
}
