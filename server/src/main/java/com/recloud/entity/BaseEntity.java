package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类
 * <p>
 * 抽取所有可变业务实体共有的审计字段，消除重复声明：
 * <ul>
 *   <li>id：自增主键</li>
 *   <li>createdAt：创建时间，INSERT 时由 {@link com.recloud.config.AutoFillMetaObjectHandler} 自动填充</li>
 *   <li>updatedAt：更新时间，INSERT/UPDATE 时自动填充</li>
 * </ul>
 * 不可变的关系表（如 annotation_like / annotation_favorite）只有创建时间、无更新语义，
 * 不继承本类，仅保留 createdAt 字段。
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
