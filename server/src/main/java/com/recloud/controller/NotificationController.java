package com.recloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.common.result.R;
import com.recloud.entity.Notification;
import com.recloud.security.SecurityUtils;
import com.recloud.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "通知管理", description = "站内信列表/未读数/标记已读")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知列表（分页）")
    @GetMapping
    public R<IPage<Notification>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(notificationService.listByUser(userId, page, size));
    }

    @Operation(summary = "未读通知数量")
    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.countUnread(userId);
        return R.ok(Map.of("count", count));
    }

    @Operation(summary = "标记单条为已读")
    @PutMapping("/{id}/read")
    public R<String> markRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.markRead(id, userId);
        return R.ok("已读");
    }

    @Operation(summary = "全部标记为已读")
    @PutMapping("/read-all")
    public R<String> markAllRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllRead(userId);
        return R.ok("全部已读");
    }
}
