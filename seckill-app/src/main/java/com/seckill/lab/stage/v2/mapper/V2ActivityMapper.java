package com.seckill.lab.stage.v2.mapper;

import com.seckill.lab.stage.v2.entity.V2ActivityEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * v2 活动表访问层。
 */
@Mapper
public interface V2ActivityMapper extends BaseMapper<V2ActivityEntity> {

    /**
     * 插入或重置活动。
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
}
