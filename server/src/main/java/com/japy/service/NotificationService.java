package com.japy.service;

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
}
