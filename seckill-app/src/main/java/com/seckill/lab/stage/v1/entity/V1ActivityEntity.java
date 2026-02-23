package com.seckill.lab.stage.v1.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1 活动实体，对应表 seckill_activity。
 */
@Data
@TableName("seckill_activity")
public class V1ActivityEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /**
     * 活动初始库存。
     */
    private Integer totalStock;

    /**
     * 当前可用库存。
     */
    private Integer availableStock;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 活动状态，1 表示可用。
     */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
