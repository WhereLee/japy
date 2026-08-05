package com.japy.module.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 小说段落表（段落为检索最小单元） */
@Data
@TableName("jf_novel_paragraph")
public class NovelParagraph {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Integer chapterNo;
    private Integer paraSeq;
    private String content;
    private Integer chars;
}
