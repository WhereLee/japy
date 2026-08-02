package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说段落（引用原文检索的数据底座）
 */
@Data
@TableName("novel_paragraph")
public class NovelParagraph {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Integer chapterNo;
    private Integer paraSeq;
    private String content;
    private Integer chars;
    private LocalDateTime createdAt;
}
