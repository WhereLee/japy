package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private Long novelId;           // 关联小说（可空）
    private Integer likeCount;
    private Integer commentCount;
    private Integer status;         // 0正常 1隐藏 2删除
    private Integer pinned;         // 置顶
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String novelTitle;      // 关联小说名（时间线填充，展示用）

    @TableField(exist = false)
    private Boolean liked;          // 当前用户是否赞过

    @TableField(exist = false)
    private List<Map<String, Object>> likedBy;  // 点赞者（前5个：userId+nickname），QQ空间式"XX、XX...觉得很赞"
}
