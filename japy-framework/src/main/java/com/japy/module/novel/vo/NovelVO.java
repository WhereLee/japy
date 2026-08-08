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
    private Long uploadElapsedMs;   // 上传解析耗时（切章+统计+入库，毫秒）
}
