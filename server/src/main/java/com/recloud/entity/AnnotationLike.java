package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批注点赞关系表（不可变关系表：只有创建时间、无更新语义，故不继承 BaseEntity）
 */
@Data
@TableName("annotation_like")
public class AnnotationLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long annotationId;
    private Long userId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
