package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageParams;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Notification;
import com.japy.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationMapper notificationMapper;

    /** 我的通知列表 */
    @GetMapping
    public R<PageResult<Notification>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        Page<Notification> result = notificationMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    /** 未读数量 */
    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        Long userId = UserContext.getUserId();
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
        return R.ok(Map.of("count", count));
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public R<Void> readAll() {
        Long userId = UserContext.getUserId();
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1));
        return R.ok();
    }

    /** 单条已读 */
    @PutMapping("/{id}/read")
    public R<Void> readOne(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, id)
                        .eq(Notification::getUserId, userId)
                        .set(Notification::getIsRead, 1));
        return R.ok();
    }
}
