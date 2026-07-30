package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("chapter")
public class Chapter extends BaseEntity {
    private Long novelId;
    private String title;
    private String content;
    private Integer chapterOrder;
}
