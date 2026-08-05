package com.japy.module.novel.vo;

import lombok.Data;

import java.util.List;

/** 章节内容响应：段落数组 + 上下章（服务端计算） */
@Data
public class ChapterVO {
    private Long id;
    private Long novelId;
    private Integer chapterNo;
    private String title;
    private Integer chars;
    private List<String> paragraphs;
    private Long prevChapterId;
    private Long nextChapterId;
}
