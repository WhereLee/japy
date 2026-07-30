package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("annotation")
public class Annotation extends BaseEntity {
    private Long chapterId;
    private Long userId;
    private Integer anchorStart;
    private Integer anchorEnd;
    private String selectedText;
    private String content;
    private Integer type;
    private Integer likeCount;
    private Integer commentCount;
    @Version
    private Integer version;
}
