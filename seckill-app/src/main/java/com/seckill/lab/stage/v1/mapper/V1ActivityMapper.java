package com.seckill.lab.stage.v1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.lab.stage.v1.entity.V1ActivityEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * v1 活动表访问层。
 */
@Mapper
public interface V1ActivityMapper extends BaseMapper<V1ActivityEntity> {

    /**
     * 插入或重置活动。
     *
     * <p>用于教学实验反复 reset：活动已存在时覆盖库存并将版本归零。
     */
    @Update("""
            INSERT INTO seckill_activity (
                id,
                title,
                start_time,
                end_time,
                total_stock,
                available_stock,
                version,
                status
            ) VALUES (
                #{activityId},
                #{title},
                #{startTime},
                #{endTime},
                #{totalStock},
                #{availableStock},
                0,
                1
            )
            ON DUPLICATE KEY UPDATE
                title = VALUES(title),
                start_time = VALUES(start_time),
                end_time = VALUES(end_time),
                total_stock = VALUES(total_stock),
                available_stock = VALUES(available_stock),
                version = 0,
                status = 1
            """)
    int upsertActivity(
            @Param("activityId") long activityId,
            @Param("title") String title,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("totalStock") int totalStock,
            @Param("availableStock") int availableStock
    );

    /**
     * 基于版本号扣减库存（乐观锁）。
     *
     * <p>返回 1 代表扣减成功，返回 0 代表版本冲突或库存不足。
     */
    @Update("""
            UPDATE seckill_activity
            SET available_stock = available_stock - 1,
                version = version + 1
            WHERE id = #{activityId}
              AND status = 1
              AND available_stock > 0
              AND version = #{expectedVersion}
            """)
    int deductStockWithVersion(
            @Param("activityId") long activityId,
            @Param("expectedVersion") int expectedVersion
    );

    /**
     * 回补 1 个库存（用于唯一键冲突时的补偿）。
     */
    @Update("""
            UPDATE seckill_activity
            SET available_stock = available_stock + 1,
                version = version + 1
            WHERE id = #{activityId}
              AND status = 1
            """)
    int restoreOneStock(@Param("activityId") long activityId);
}
