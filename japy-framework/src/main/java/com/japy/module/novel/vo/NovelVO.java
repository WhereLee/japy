package com.japy.module.novel.vo;

import lombok.Data;

/** 小说列表/详情 VO */
@Data
public class NovelVO {
    private Long id;
    private String title;
    private String author;
    private String intro;
    private String cover;
    private String category;
    private Integer status;
    private Integer chapterCount;
    private Long totalChars;
}
