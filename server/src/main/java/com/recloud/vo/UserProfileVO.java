package com.recloud.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户个人主页 VO
 * <p>
 * 聚合展示：
 * - 基本信息（昵称、角色、注册时间）
 * - 社区贡献统计（批注数、获赞总数、获评论总数）
 * - 收藏统计（收藏数）
 * - 最近批注列表（最近 10 条）
 */
@Data
@Schema(description = "用户个人主页")
public class UserProfileVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "角色")
    private String role;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "注册时间")
    private LocalDateTime registeredAt;

    // ===== 社区贡献统计 =====

    @Schema(description = "批注总数")
    private long annotationCount;

    @Schema(description = "获赞总数（所有批注的 like_count 之和）")
    private long totalLikesReceived;

    @Schema(description = "获评论总数（所有批注的 comment_count 之和）")
    private long totalCommentsReceived;

    @Schema(description = "收藏批注数")
    private long favoriteCount;

    // ===== 最近活动 =====

    @Schema(description = "最近批注列表（最近10条）")
    private List<AnnotationVO> recentAnnotations;
}
