package com.seckill.lab.stage.v2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2 订单实体，对应表 seckill_order。
 */
@Data
@TableName("seckill_order")
public class V2OrderEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long activityId;

    private String userId;

    private Integer orderStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
