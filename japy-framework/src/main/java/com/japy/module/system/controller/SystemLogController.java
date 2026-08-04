package com.japy.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.aspect.OperLog;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.module.system.entity.SysLoginLog;
import com.japy.module.system.entity.SysOperLog;
import com.japy.module.system.mapper.SysLoginLogMapper;
import com.japy.module.system.mapper.SysOperLogMapper;
import com.japy.security.LoginUser;
import com.japy.security.RedisSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端：操作日志 / 登录日志 / 在线用户 / 仪表盘
 */
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemLogController {

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final com.japy.module.user.mapper.SysUserMapper userMapper;
    private final RedisSessionService sessionService;
    private final StringRedisTemplate redis;

    // ==================== 操作日志 ====================

    @GetMapping("/operlog/list")
    @PreAuthorize("@ss.hasPermi('system:operlog:list')")
    public R<PageResult<SysOperLog>> operLogList(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Page<SysOperLog> p = operLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysOperLog>().orderByDesc(SysOperLog::getId));
        return R.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @DeleteMapping("/operlog/clean")
    @PreAuthorize("@ss.hasPermi('system:operlog:clean')")
    @OperLog(title = "操作日志", businessType = 3)
    public R<Void> cleanOperLog() {
        operLogMapper.delete(null);
        return R.ok();
    }

    // ==================== 登录日志 ====================

    @GetMapping("/loginlog/list")
    @PreAuthorize("@ss.hasPermi('system:loginlog:list')")
    public R<PageResult<SysLoginLog>> loginLogList(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Page<SysLoginLog> p = loginLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysLoginLog>().orderByDesc(SysLoginLog::getId));
        return R.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    // ==================== 在线用户 ====================

    @GetMapping("/online/list")
    @PreAuthorize("@ss.hasPermi('system:online:list')")
    public R<List<Map<String, Object>>> onlineList() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Set<String> keys = redis.keys("login:user:*");
            for (String key : keys) {
                String userId = key.substring("login:user:".length());
                LoginUser lu = sessionService.getLoginUser(Long.valueOf(userId));
                if (lu == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", userId);
                m.put("username", lu.getUsername());
                m.put("nickname", lu.getUser().getNickname());
                m.put("roles", lu.getRoles());
                result.add(m);
            }
        } catch (Exception ignored) {
        }
        return R.ok(result);
    }

    @DeleteMapping("/online/{userId}")
    @PreAuthorize("@ss.hasPermi('system:online:forceLogout')")
    @OperLog(title = "在线用户", businessType = 3)
    public R<Void> forceLogout(@PathVariable Long userId) {
        sessionService.removeSession(userId);
        return R.ok();
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/dashboard")
    @PreAuthorize("@ss.hasPermi('dashboard:view')")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(new LambdaQueryWrapper<com.japy.module.user.entity.SysUser>()
                .eq(com.japy.module.user.entity.SysUser::getDelFlag, 0)));
        data.put("operLogCount", operLogMapper.selectCount(null));
        data.put("loginLogCount", loginLogMapper.selectCount(null));
        data.put("onlineCount", onlineList().getData() == null ? 0 : onlineList().getData().size());
        return R.ok(data);
    }
}
