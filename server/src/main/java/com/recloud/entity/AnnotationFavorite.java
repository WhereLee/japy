package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批注收藏实体
 * <p>
 * 用户可以收藏有价值的批注，方便后续回顾。
 * 收藏是用户维度的操作，不影响批注本身的计数。
 * 不可变关系表：只有创建时间、无更新语义，故不继承 BaseEntity。
 */
@Data
@TableName("annotation_favorite")
public class AnnotationFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏用户ID */
    private Long userId;

    /** 被收藏的批注ID */
    private Long annotationId;

    /** 收藏时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
