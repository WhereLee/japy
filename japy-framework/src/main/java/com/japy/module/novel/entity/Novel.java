package com.japy.module.novel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
    private Integer status;          // 0上架(可读) 1下架 2草稿
    private Integer chapterCount;    // 冗余：章节数
    private Long totalChars;         // 冗余：总字数
    private String filePath;         // 源文件目录（data/novels/{id}_{title}）
    @TableLogic
    private Integer delFlag;         // 0正常 1逻辑删除
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
