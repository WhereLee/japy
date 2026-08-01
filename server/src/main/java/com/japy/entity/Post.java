package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("post")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Long userId;
    private String nickname;
    private String content;
    private String quoteText;
    private Integer likeCount;
    private Integer commentCount;
    private Integer status;
    private Integer pinned;
    private Integer featured;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Boolean liked;
    @TableField(exist = false)
    private Integer level;
}
