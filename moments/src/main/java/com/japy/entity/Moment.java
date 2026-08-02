package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 动态（说说）
 */
@Data
@TableName("moment")
public class Moment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String nickname;        // 发布时快照
    private String content;
    private Integer likeCount;
    private Integer commentCount;
    private Integer status;         // 0正常 1隐藏 2删除
    private Integer pinned;         // 置顶
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Boolean liked;          // 当前用户是否赞过
}
