package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("annotation_comment")
public class Comment extends BaseEntity {
    private Long annotationId;
    private Long userId;
    private Long replyToId;
    private String content;
}
