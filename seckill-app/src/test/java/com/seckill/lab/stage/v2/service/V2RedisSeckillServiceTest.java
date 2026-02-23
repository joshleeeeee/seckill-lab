package com.seckill.lab.stage.v2.service;

import com.seckill.lab.stage.v2.entity.V2ActivityEntity;
import com.seckill.lab.stage.v2.entity.V2OrderEntity;
import com.seckill.lab.stage.v2.mapper.V2ActivityMapper;
import com.seckill.lab.stage.v2.mapper.V2OrderMapper;
import com.seckill.lab.stage.v2.model.V2AttemptResult;
import com.seckill.lab.stage.v2.model.V2Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2RedisSeckillServiceTest {

    private static final long ACTIVITY_ID = 1001L;

    @Mock
    private V2ActivityMapper activityMapper;

    @Mock
    private V2OrderMapper orderMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private V2RedisSeckillService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void rejectsWhenActivityIsMissing() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

        V2AttemptResult result = service.attempt(ACTIVITY_ID, "u-miss");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("ACTIVITY_NOT_FOUND");
        verify(orderMapper, never()).countByActivityAndUser(anyLong(), anyString());
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList());
    }

    @Test
    void rejectsDuplicateOrderBeforeRedisDeduction() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity(ACTIVITY_ID, 50, 50));
        when(orderMapper.countByActivityAndUser(ACTIVITY_ID, "u-1")).thenReturn(1L);

        V2AttemptResult result = service.attempt(ACTIVITY_ID, "u-1");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("DUPLICATE_ORDER");
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList());
    }

    @Test
    void returnsSoldOutWhenLuaReportsSoldOut() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity(ACTIVITY_ID, 10, 10));
        when(orderMapper.countByActivityAndUser(ACTIVITY_ID, "u-2")).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(-1L);

        V2AttemptResult result = service.attempt(ACTIVITY_ID, "u-2");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("SOLD_OUT");
        verify(orderMapper, never()).insert(anyOrder());
    }

    @Test
    void createsOrderWhenRedisDeductSucceeds() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity(ACTIVITY_ID, 10, 10));
        when(orderMapper.countByActivityAndUser(ACTIVITY_ID, "u-3")).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(9L);
        when(orderMapper.insert(anyOrder())).thenReturn(1);

        V2AttemptResult result = service.attempt(ACTIVITY_ID, "u-3");

        assertThat(result.success()).isTrue();
        assertThat(result.note()).isEqualTo("OK");
        assertThat(result.orderNo()).isNotBlank();
        assertThat(result.remainingStock()).isEqualTo(9);
    }

    @Test
    void restoresRedisStockWhenInsertHitsUniqueConstraint() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity(ACTIVITY_ID, 10, 10));
        when(orderMapper.countByActivityAndUser(ACTIVITY_ID, "u-dup")).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(9L);
        when(orderMapper.insert(anyOrder())).thenThrow(new DuplicateKeyException("uk_activity_user"));
        when(valueOperations.increment(stockKey(ACTIVITY_ID))).thenReturn(10L);

        V2AttemptResult result = service.attempt(ACTIVITY_ID, "u-dup");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("DUPLICATE_ORDER");
        assertThat(result.remainingStock()).isEqualTo(10);
        verify(valueOperations).increment(stockKey(ACTIVITY_ID));
    }

    @Test
    void warmsUpRedisStockWhenLuaReportsMissingKey() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity(ACTIVITY_ID, 5, 5));
        when(orderMapper.countByActivityAndUser(ACTIVITY_ID, "u-4")).thenReturn(0L);
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(-2L, 4L);
        when(orderMapper.countByActivityId(ACTIVITY_ID)).thenReturn(1L);
        when(valueOperations.setIfAbsent(stockKey(ACTIVITY_ID), "4")).thenReturn(true);
        when(orderMapper.insert(anyOrder())).thenReturn(1);

        V2AttemptResult result = service.attempt(ACTIVITY_ID, "u-4");

        assertThat(result.success()).isTrue();
        assertThat(result.remainingStock()).isEqualTo(4);
        verify(valueOperations).setIfAbsent(stockKey(ACTIVITY_ID), "4");
    }

    @Test
    void snapshotPrefersRedisStockWhenKeyExists() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activeActivity(ACTIVITY_ID, 50, 50));
        when(orderMapper.countByActivityId(ACTIVITY_ID)).thenReturn(5L);
        when(orderMapper.countDuplicateUsers(ACTIVITY_ID)).thenReturn(0L);
        when(orderMapper.selectRecentOrderTraces(ACTIVITY_ID)).thenReturn(List.of());
        when(valueOperations.get(stockKey(ACTIVITY_ID))).thenReturn("45");

        V2Snapshot snapshot = service.snapshot(ACTIVITY_ID);

        assertThat(snapshot.totalStock()).isEqualTo(50);
        assertThat(snapshot.availableStock()).isEqualTo(45);
        assertThat(snapshot.totalOrders()).isEqualTo(5);
        assertThat(snapshot.oversoldUnits()).isEqualTo(0);
    }

    private V2ActivityEntity activeActivity(long activityId, int totalStock, int availableStock) {
        V2ActivityEntity activity = new V2ActivityEntity();
        activity.setId(activityId);
        activity.setStatus(1);
        activity.setTotalStock(totalStock);
        activity.setAvailableStock(availableStock);
        return activity;
    }

    private V2OrderEntity anyOrder() {
        return org.mockito.ArgumentMatchers.any(V2OrderEntity.class);
    }

    private String stockKey(long activityId) {
        return "seckill:v2:stock:" + activityId;
    }
}
