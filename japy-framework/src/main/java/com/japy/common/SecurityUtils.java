package com.japy.common;

import com.japy.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前登录用户工具 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu;
        }
        return null;
    }

    public static Long userId() {
        LoginUser lu = currentUser();
        return lu == null ? null : lu.getUserId();
    }

    public static String nickname() {
        LoginUser lu = currentUser();
        return lu == null ? "未知" : lu.getNickname();
    }
}
