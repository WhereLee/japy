package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.entity.Notification;
import com.recloud.entity.User;
import com.recloud.mapper.NotificationMapper;
import com.recloud.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 站内通知服务
 * <p>
 * 设计思路（模板方法模式）：
 * 每种通知类型有独立的发送方法，内部统一调用 send() 写入 DB。
 * 管理员操作（封禁/重置密码/处理举报）时触发对应通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    /**
     * 查询用户通知列表（分页，最新优先）
     */
    public IPage<Notification> listByUser(Long userId, int page, int size) {
        Page<Notification> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);
        return notificationMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 查询未读通知数量
     */
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
        );
    }

    /**
     * 标记单条通知为已读
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long notificationId, Long userId) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getUserId, userId)
                        .set(Notification::getIsRead, 1)
        );
    }

    /**
     * 标记全部通知为已读
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1)
        );
    }

    /**
     * 群发全体用户通知（系统公告）
     * <p>
     * 管理员向所有用户发送一条站内公告，每个用户生成一条独立的 notification 记录。
     * 采用“每人一行”而非“广播表”设计，与现有已读/未读、分页查询逻辑天然兼容。
     *
     * @return 实际发送的用户数
     */
    @Transactional(rollbackFor = Exception.class)
    public int sendBroadcast(String title, String content) {
        // 查询所有用户 ID（包含禁用用户，公告面向全体注册用户）
        List<Long> userIds = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId)
        ).stream().map(User::getId).toList();

        for (Long userId : userIds) {
            send(userId, "announcement", title, content, null);
        }
        log.info("群发公告完成: title={}, 共发送 {} 人", title, userIds.size());
        return userIds.size();
    }

    // ==================== 通知发送（模板方法） ====================

    /**
     * 发送账号封禁通知
     */
    public void sendBanNotification(Long userId, String reason) {
        send(userId, "ban", "账号已被封禁",
                reason != null ? "您的账号因以下原因被封禁：" + reason : "您的账号已被管理员封禁。如有疑问请联系管理员。",
                null);
    }

    /**
     * 发送账号解封通知
     */
    public void sendUnbanNotification(Long userId) {
        send(userId, "unban", "账号已解封",
                "您的账号已恢复正常使用。请遵守社区规范，文明发言。",
                null);
    }

    /**
     * 发送密码重置通知
     */
    public void sendPasswordResetNotification(Long userId) {
        send(userId, "password_reset", "密码已被重置",
                "您的登录密码已被管理员重置。请使用新密码登录，并尽快修改密码。",
                null);
    }

    /**
     * 发送举报成立通知（给举报人）
     */
    public void sendReportResolvedNotification(Long userId, Long reportId) {
        send(userId, "report_resolved", "您举报的内容已处理",
                "您举报的违规内容经管理员核实已成立，相关内容已被删除。感谢您的监督。",
                reportId);
    }

    /**
     * 发送举报驳回通知（给举报人）
     */
    public void sendReportRejectedNotification(Long userId, Long reportId, String handleNote) {
        String content = "您举报的内容经管理员核实，未构成违规，举报已驳回。";
        if (handleNote != null && !handleNote.isEmpty()) {
            content += "管理员备注：" + handleNote;
        }
        send(userId, "report_rejected", "您举报的内容已驳回", content, reportId);
    }

    /**
     * 发送点赞通知（给批注作者）
     * <p>
     * 触发时机：用户A点赞了用户B的批注
     * 防骚扰：同一批注的重复点赞不重复通知（由调用方控制）
     */
    public void sendLikeNotification(Long annotationAuthorId, Long likerId, Long annotationId) {
        // 不通知自己
        if (annotationAuthorId.equals(likerId)) return;
        send(annotationAuthorId, "like",
                "有人赞了你的批注",
                "你的批注获得了新的点赞，去看看是谁吧。",
                annotationId);
    }

    /**
     * 发送评论通知（给批注作者）
     * <p>
     * 触发时机：用户A在用户B的批注下评论
     */
    public void sendCommentNotification(Long annotationAuthorId, Long commenterId,
                                        Long annotationId, String commentContent) {
        // 不通知自己
        if (annotationAuthorId.equals(commenterId)) return;
        String preview = commentContent.length() > 30
                ? commentContent.substring(0, 30) + "..."
                : commentContent;
        send(annotationAuthorId, "comment",
                "有人评论了你的批注",
                "有人在你的批注下评论：" + preview,
                annotationId);
    }

    /**
     * 发送回复通知（给被回复的评论作者）
     * <p>
     * 触发时机：用户A回复了用户B的评论
     */
    public void sendReplyNotification(Long replyToAuthorId, Long replierId,
                                      Long annotationId, String commentContent) {
        // 不通知自己
        if (replyToAuthorId.equals(replierId)) return;
        String preview = commentContent.length() > 30
                ? commentContent.substring(0, 30) + "..."
                : commentContent;
        send(replyToAuthorId, "reply",
                "有人回复了你的评论",
                "有人回复了你的评论：" + preview,
                annotationId);
    }

    /**
     * 统一发送通知（核心方法）
     */
    private void send(Long userId, String type, String title, String content, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(0);
        notification.setRelatedId(relatedId);
        notificationMapper.insert(notification);
        log.info("站内通知已发送: userId={}, type={}, title={}", userId, type, title);
    }
}
