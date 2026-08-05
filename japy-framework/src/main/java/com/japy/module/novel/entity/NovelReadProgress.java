package com.japy.module.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 小说阅读进度表（用户×小说唯一） */
@Data
@TableName("jf_novel_read_progress")
public class NovelReadProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long novelId;
    private Long chapterId;
    private Integer charOffset;      // 章内字符偏移（跨设备稳定）
    private BigDecimal percent;      // 章内百分比 0-100
    private LocalDateTime updateTime;
}
