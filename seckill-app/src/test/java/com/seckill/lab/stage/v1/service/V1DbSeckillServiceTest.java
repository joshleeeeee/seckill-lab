package com.seckill.lab.stage.v1.service;

import com.seckill.lab.stage.v1.entity.V1ActivityEntity;
import com.seckill.lab.stage.v1.entity.V1OrderEntity;
import com.seckill.lab.stage.v1.mapper.V1ActivityMapper;
import com.seckill.lab.stage.v1.mapper.V1OrderMapper;
import com.seckill.lab.stage.v1.model.V1AttemptResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V1DbSeckillServiceTest {

    @Mock
    private V1ActivityMapper activityMapper;

    @Mock
    private V1OrderMapper orderMapper;

    @InjectMocks
    private V1DbSeckillService service;

    @Test
    void rejectsDuplicateOrderBeforeStockDeduction() {
        when(activityMapper.selectById(1001L)).thenReturn(activeActivity(1001L, 50, 1));
        when(orderMapper.countByActivityAndUser(1001L, "u-1")).thenReturn(1L);

        V1AttemptResult result = service.attempt(1001L, "u-1");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("DUPLICATE_ORDER");
        verify(activityMapper, never()).deductStockWithVersion(anyLong(), anyInt());
    }

    @Test
    void rejectsWhenSoldOut() {
        when(activityMapper.selectById(1001L)).thenReturn(activeActivity(1001L, 0, 2));

        V1AttemptResult result = service.attempt(1001L, "u-2");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("SOLD_OUT");
        verify(orderMapper, never()).insert(anyOrder());
    }

    @Test
    void createsOrderAfterSuccessfulOptimisticStockDeduction() {
        when(activityMapper.selectById(1001L)).thenReturn(activeActivity(1001L, 10, 3));
        when(orderMapper.countByActivityAndUser(1001L, "u-3")).thenReturn(0L);
        when(activityMapper.deductStockWithVersion(1001L, 3)).thenReturn(1);
        when(orderMapper.insert(anyOrder())).thenReturn(1);

        V1AttemptResult result = service.attempt(1001L, "u-3");

        assertThat(result.success()).isTrue();
        assertThat(result.note()).isEqualTo("OK");
        assertThat(result.orderNo()).isNotBlank();
        assertThat(result.remainingStock()).isEqualTo(9);
    }

    @Test
    void returnsConflictAfterRetryExhausted() {
        when(activityMapper.selectById(1001L)).thenReturn(activeActivity(1001L, 20, 5));
        when(orderMapper.countByActivityAndUser(1001L, "u-4")).thenReturn(0L);
        when(activityMapper.deductStockWithVersion(1001L, 5)).thenReturn(0);

        V1AttemptResult result = service.attempt(1001L, "u-4");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("STOCK_CONFLICT_RETRY_EXHAUSTED");
        verify(activityMapper, times(3)).deductStockWithVersion(1001L, 5);
        verify(orderMapper, never()).insert(anyOrder());
    }

    @Test
    void rejectsWhenActivityIsMissing() {
        when(activityMapper.selectById(2001L)).thenReturn(null);

        V1AttemptResult result = service.attempt(2001L, "u-miss");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("ACTIVITY_NOT_FOUND");
        verify(orderMapper, never()).countByActivityAndUser(anyLong(), anyString());
    }

    @Test
    void restoresStockWhenInsertHitsUniqueConstraint() {
        when(activityMapper.selectById(1001L)).thenReturn(activeActivity(1001L, 10, 3));
        when(orderMapper.countByActivityAndUser(1001L, "u-dup")).thenReturn(0L);
        when(activityMapper.deductStockWithVersion(1001L, 3)).thenReturn(1);
        when(orderMapper.insert(anyOrder())).thenThrow(new DuplicateKeyException("uk_activity_user"));

        V1AttemptResult result = service.attempt(1001L, "u-dup");

        assertThat(result.success()).isFalse();
        assertThat(result.note()).isEqualTo("DUPLICATE_ORDER");
        verify(activityMapper).restoreOneStock(1001L);
    }

    private V1ActivityEntity activeActivity(long activityId, int availableStock, int version) {
        V1ActivityEntity activity = new V1ActivityEntity();
        activity.setId(activityId);
        activity.setStatus(1);
        activity.setTotalStock(50);
        activity.setAvailableStock(availableStock);
        activity.setVersion(version);
        return activity;
    }

    private V1OrderEntity anyOrder() {
        return org.mockito.ArgumentMatchers.any(V1OrderEntity.class);
    }
}
