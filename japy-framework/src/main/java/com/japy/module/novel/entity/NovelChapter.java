package com.japy.module.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 小说章节表 */
@Data
@TableName("jf_novel_chapter")
public class NovelChapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Integer chapterNo;
    private String title;
    private Integer chars;           // 冗余：本章字数
    private Integer paragraphCount;  // 冗余：本章段数
    private LocalDateTime createTime;
}
