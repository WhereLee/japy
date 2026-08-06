package com.japy.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 敏感词库 */
@Data
@TableName("jf_sensitive_word")
public class SensitiveWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private String category;   // 政治/色情/暴力/广告/其他
    private Integer level;     // 1高危 2低危
    private Integer status;    // 0启用 1停用
    private LocalDateTime createTime;
}
