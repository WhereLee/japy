package com.japy.module.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 小说主表 */
@Data
@TableName("jf_novel")
public class Novel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private String intro;
    private String cover;
    private String category;
    private Integer status;          // 0连载 1完结
    private Integer chapterCount;    // 冗余：章节数
    private Long totalChars;         // 冗余：总字数
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
