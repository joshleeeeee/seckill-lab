package com.seckill.lab.stage.v1.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1 订单实体，对应表 seckill_order。
 */
@Data
@TableName("seckill_order")
public class V1OrderEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务订单号。
     */
    private String orderNo;

    private Long activityId;

    private String userId;

    /**
     * 订单状态，v1 默认 0。
     */
    private Integer orderStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
