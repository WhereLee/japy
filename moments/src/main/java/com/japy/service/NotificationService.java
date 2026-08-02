package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.entity.Notification;
import com.japy.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /** 发送通知 */
    public void send(Long userId, String type, String refType, Long refId, String content) {
        if (userId == null) return;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setContent(content);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    /**
     * 点赞通知（节流）：同一动态对同一收件人若已有未读的点赞通知，则不再重复发送。
     * 经典教训：反复"点赞-取消-点赞"会刷爆通知（微博/微信均做合并）。
     */
    public void sendLike(Long userId, Long momentId, String content) {
        if (userId == null) return;
        Long unread = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getType, "like")
                        .eq(Notification::getRefType, "moment")
                        .eq(Notification::getRefId, momentId)
                        .eq(Notification::getIsRead, 0));
        if (unread > 0) return; // 已有未读点赞通知，节流
        send(userId, "like", "moment", momentId, content);
    }
}
