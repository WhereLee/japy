package com.recloud.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 站内通知实体
 * <p>
 * 通知类型：
 * - ban: 账号封禁通知
 * - unban: 账号解封通知
 * - password_reset: 密码重置通知
 * - report_resolved: 举报成立通知
 * - report_rejected: 举报驳回通知
 * - like: 点赞通知（互动）
 * - comment: 评论通知（互动）
 * - reply: 回复通知（互动）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    /** 接收用户ID */
    private Long userId;

    /** 通知类型：ban/unban/password_reset/report_resolved/report_rejected/like/comment/reply */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 是否已读：0=未读 1=已读 */
    private Integer isRead;

    /** 关联业务ID（如举报ID） */
    private Long relatedId;
}
