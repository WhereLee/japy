package com.japy.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.module.auth.vo.RouterVo;
import com.japy.module.user.entity.SysPermission;
import com.japy.module.user.mapper.SysPermissionMapper;
import com.japy.module.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单树服务：按当前用户可见权限组装前端路由树（若依模式）。
 * - 超管（admin 角色）：返回全部目录/菜单（权限由 *:*:* 通配覆盖）
 * - 其他角色：按 role_permission 关联查可见目录/菜单
 * - 按钮权限点（perm_type=3）不参与路由，前端按钮级校验走 /auth/info 返回的 perms 数组
 */
@Service
@RequiredArgsConstructor
public class MenuTreeService {

    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    /** 查询用户可见的目录/菜单节点 */
    public List<SysPermission> listMenusByUser(Long userId, boolean isAdmin) {
        if (isAdmin) {
            return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getStatus, 0)
                    .orderByAsc(SysPermission::getSort));
        }
        List<Long> permIds = userRoleMapper.selectPermIds(userId);
        if (permIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getId, permIds)
                .eq(SysPermission::getStatus, 0)
                .orderByAsc(SysPermission::getSort));
    }

    /** 组装路由树（只保留目录+菜单） */
    public List<RouterVo> buildRouterTree(List<SysPermission> perms) {
        List<SysPermission> nodes = perms.stream()
                .filter(p -> p.getPermType() != null && p.getPermType() <= 2)
                .collect(Collectors.toList());
        return buildChildren(nodes, 0L);
    }

    private List<RouterVo> buildChildren(List<SysPermission> nodes, Long parentId) {
        return nodes.stream()
                .filter(n -> parentId.equals(n.getParentId()))
                .sorted(Comparator.comparing(SysPermission::getSort, Comparator.nullsLast(Integer::compareTo)))
                .map(n -> {
                    RouterVo vo = new RouterVo();
                    vo.setName("Route" + n.getId());
                    vo.setPath(n.getPath());
                    vo.setMeta(new RouterVo.Meta(n.getPermName(), n.getIcon(), false));
                    boolean isDir = n.getPermType() != null && n.getPermType() == 1;
                    if (isDir) {
                        vo.setComponent("Layout");
                        vo.setRedirect(n.getPath());
                        vo.setChildren(buildChildren(nodes, n.getId()));
                    } else {
                        vo.setComponent(n.getComponent());
                        vo.setChildren(List.of());
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
