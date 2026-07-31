package com.japy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("post_favorite")
public class PostFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long postId;
    private LocalDateTime createdAt;
}
