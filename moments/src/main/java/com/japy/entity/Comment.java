package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评论（支持一层楼中楼）
 */
@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long momentId;
    private Long userId;
    private String nickname;
    private Long parentId;          // NULL=顶层评论，否则回复某条顶层评论
    private String replyTo;         // 被回复人昵称快照
    private String content;
    private Integer status;         // 0正常 1隐藏 2删除
    private LocalDateTime createdAt;
}
