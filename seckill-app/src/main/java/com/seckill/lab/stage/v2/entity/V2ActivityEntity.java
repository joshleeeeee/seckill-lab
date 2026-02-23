package com.seckill.lab.stage.v2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2 活动实体，对应表 seckill_activity。
 */
@Data
@TableName("seckill_activity")
public class V2ActivityEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalStock;

    private Integer availableStock;

    private Integer version;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
