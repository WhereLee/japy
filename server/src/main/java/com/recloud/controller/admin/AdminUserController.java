package com.recloud.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.annotation.Log;
import com.recloud.common.result.R;
import com.recloud.entity.User;
import com.recloud.service.UserService;
import com.recloud.vo.UserVO;
import com.recloud.vo.VOConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@Tag(name = "管理端-用户管理", description = "用户列表/禁用/启用/重置密码")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "用户列表（分页+搜索）")
    @GetMapping
    public R<IPage<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        IPage<User> userPage = userService.listUsers(page, size, keyword);
        // Entity → VO 转换，排除 password 等敏感字段
        IPage<UserVO> voPage = new Page<>(page, size, userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream()
                .map(VOConverter::toVO)
                .collect(Collectors.toList()));
        return R.ok(voPage);
    }

    @Operation(summary = "禁用/启用用户")
    @PutMapping("/{id}/status")
    @Log(module = "用户管理", operation = "修改用户状态")
    public R<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok(status != null && status == 1 ? "已启用" : "已禁用");
    }

    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/reset-password")
    @Log(module = "用户管理", operation = "重置密码")
    public R<String> resetPassword(@PathVariable Long id) {
        String newPassword = userService.resetPassword(id);
        return R.ok("密码已重置为: " + newPassword);
    }
}
