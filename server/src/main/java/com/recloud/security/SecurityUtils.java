package com.recloud.security;

import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类 — 统一获取当前登录用户信息
 * <p>
 * 消除各 Service/Controller 中重复的 getCurrentUserId() 私有方法，
 * 提供静态方法直接调用，无需注入。
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 当前用户 ID
     * @throws BizException 未登录时抛出 UNAUTHORIZED
     */
    public static Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        throw new BizException(ResultCode.UNAUTHORIZED);
    }

    /**
     * 获取当前登录用户完整对象
     *
     * @return 当前 LoginUser，未登录返回 null
     */
    public static LoginUser getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
