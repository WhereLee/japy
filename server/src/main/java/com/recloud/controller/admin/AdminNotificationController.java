package com.recloud.controller.admin;

import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.dto.request.BroadcastNotificationRequest;
import com.recloud.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理端-通知管理", description = "群发全体用户站内公告")
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "群发全体用户公告")
    @PostMapping("/broadcast")
    @Log(module = "通知管理", operation = "群发全体公告")
    public R<Map<String, Object>> broadcast(@Valid @RequestBody BroadcastNotificationRequest request) {
        int count = notificationService.sendBroadcast(request.getTitle(), request.getContent());
        return R.ok(Map.of("success", true, "recipientCount", count));
    }
}
