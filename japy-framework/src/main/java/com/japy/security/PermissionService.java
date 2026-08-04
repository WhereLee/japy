package com.japy.security;

import com.japy.common.SecurityUtils;
import com.japy.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 权限校验服务（若依 @ss 模式）：供 @PreAuthorize("@ss.hasPermi('xxx')") 使用。
 * 支持通配：权限集合含 *:*:* 时全部放行（超管）。
 */
@Service("ss")
public class PermissionService {

    public static final String ALL_PERMISSION = "*:*:*";

    /** 当前登录用户是否拥有某权限（含 *:*:* 通配） */
    public boolean hasPermi(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.currentUser();
        if (loginUser == null) {
            return false;
        }
        return hasPermi(loginUser.getPerms(), permission);
    }

    public boolean hasPermi(Collection<String> perms, String permission) {
        if (perms == null || perms.isEmpty()) {
            return false;
        }
        return perms.contains(ALL_PERMISSION) || perms.contains(permission);
    }

    /** 当前登录用户是否拥有某角色 */
    public boolean hasRole(String role) {
        LoginUser loginUser = SecurityUtils.currentUser();
        if (loginUser == null) {
            return false;
        }
        Collection<String> roles = loginUser.getRoles();
        return roles != null && roles.contains(role);
    }
}
