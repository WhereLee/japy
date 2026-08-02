package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说章节（含每章统计信息）
 */
@Data
@TableName("novel_chapter")
public class NovelChapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Integer chapterNo;
    private String title;
    private Integer chars;
    private Integer paragraphCount;
    private Integer maxParaChars;
    private LocalDateTime createdAt;
}
