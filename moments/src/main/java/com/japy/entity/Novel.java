package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小说（管理端上传入库）
 */
@Data
@TableName("novel")
public class Novel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private Integer status;             // 0建设中 1已入库 2入库失败
    private Integer chapterCount;
    private Integer paragraphCount;
    private Integer totalChars;
    private String sourceName;          // 源文件名
    private Long sourceSize;            // 源文件大小（字节）
    private String sourceEncoding;      // 源文件编码
    private String dirPath;             // 落盘目录
    private LocalDateTime createdAt;
}
