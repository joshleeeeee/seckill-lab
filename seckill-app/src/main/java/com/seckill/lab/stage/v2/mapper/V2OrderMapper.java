package com.seckill.lab.stage.v2.mapper;

import com.seckill.lab.stage.v2.entity.V2OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * v2 订单表访问层。
 */
@Mapper
public interface V2OrderMapper extends BaseMapper<V2OrderEntity> {

    /**
     * 删除指定活动的订单（用于 reset）。
     */
    @Delete("DELETE FROM seckill_order WHERE activity_id = #{activityId}")
    int deleteByActivityId(@Param("activityId") long activityId);

    /**
     * 统计某活动下某用户订单数（应用层重复下单预检查）。
     */
    @Select("SELECT COUNT(1) FROM seckill_order WHERE activity_id = #{activityId} AND user_id = #{userId}")
    long countByActivityAndUser(
            @Param("activityId") long activityId,
            @Param("userId") String userId
    );

    /**
     * 统计某活动总订单数。
     */
    @Select("SELECT COUNT(1) FROM seckill_order WHERE activity_id = #{activityId}")
    long countByActivityId(@Param("activityId") long activityId);

    /**
     * 统计“下单次数大于 1”的用户数量。
     */
    @Select("""
            SELECT COUNT(1)
            FROM (
                SELECT user_id
                FROM seckill_order
                WHERE activity_id = #{activityId}
                GROUP BY user_id
                HAVING COUNT(1) > 1
            ) t
            """)
    long countDuplicateUsers(@Param("activityId") long activityId);

    /**
     * 查询最近 10 条订单轨迹（orderNo:userId）。
     */
    @Select("""
            SELECT CONCAT(order_no, ':', user_id)
            FROM seckill_order
            WHERE activity_id = #{activityId}
            ORDER BY created_at DESC
            LIMIT 10
            """)
    List<String> selectRecentOrderTraces(@Param("activityId") long activityId);
}
