package com.recloud.controller;

import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.entity.User;
import com.recloud.security.SecurityUtils;
import com.recloud.service.UserService;
import com.recloud.vo.UserProfileVO;
import com.recloud.vo.UserVO;
import com.recloud.vo.VOConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户管理", description = "获取当前用户信息/修改昵称")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public R<UserVO> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        return R.ok(VOConverter.toVO(user));
    }

    @Operation(summary = "修改昵称")
    @PutMapping("/me/nickname")
    @Log(module = "用户", operation = "修改昵称")
    public R<UserVO> updateNickname(@RequestParam @NotBlank @Size(min = 1, max = 30) String nickname) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userService.updateNickname(userId, nickname);
        return R.ok(VOConverter.toVO(user));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    @Log(module = "用户", operation = "修改密码")
    public R<String> changePassword(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null || newPassword.length() < 8) {
            return R.fail(400, "新密码至少8位");
        }
        userService.changePassword(userId, oldPassword, newPassword);
        return R.ok("密码修改成功");
    }

    @Operation(summary = "个人主页（社区贡献统计 + 最近批注）")
    @GetMapping("/me/profile")
    public R<UserProfileVO> myProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(userService.getUserProfile(userId));
    }
}
