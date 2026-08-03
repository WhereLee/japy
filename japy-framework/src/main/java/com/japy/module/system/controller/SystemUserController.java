package com.japy.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.aspect.OperLog;
import com.japy.common.BusinessException;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.module.user.entity.SysPermission;
import com.japy.module.user.entity.SysRole;
import com.japy.module.user.entity.SysUser;
import com.japy.module.user.mapper.SysPermissionMapper;
import com.japy.module.user.mapper.SysRoleMapper;
import com.japy.module.user.mapper.SysUserMapper;
import com.japy.module.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端：用户管理 / 角色管理 / 权限管理（RBAC 核心）
 */
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemUserController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    // ==================== 用户管理 ====================

    @GetMapping("/user/list")
    @PreAuthorize("hasAuthority('system:user:list')")
    public R<PageResult<SysUser>> userList(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDelFlag, 0);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword));
        }
        w.orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), w);
        p.getRecords().forEach(u -> u.setPassword(null));
        return R.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @PostMapping("/user")
    @PreAuthorize("hasAuthority('system:user:add')")
    @OperLog(title = "用户管理", businessType = 1)
    public R<Void> addUser(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getPassword() == null) throw new BusinessException("参数不完整");
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername()).eq(SysUser::getDelFlag, 0));
        if (exists > 0) throw new BusinessException("用户名已存在");
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(user.getStatus() == null ? 0 : user.getStatus());
        user.setDelFlag(0);
        userMapper.insert(user);
        return R.ok();
    }

    @PutMapping("/user")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @OperLog(title = "用户管理", businessType = 2)
    public R<Void> editUser(@RequestBody SysUser user) {
        SysUser db = userMapper.selectById(user.getId());
        if (db == null) throw new BusinessException("用户不存在");
        db.setNickname(user.getNickname());
        db.setEmail(user.getEmail());
        db.setPhone(user.getPhone());
        db.setSex(user.getSex());
        userMapper.updateById(db);
        return R.ok();
    }

    @DeleteMapping("/user/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @OperLog(title = "用户管理", businessType = 3)
    public R<Void> deleteUser(@PathVariable Long id) {
        if (id == 1) throw new BusinessException("不能删除内置管理员");
        SysUser u = userMapper.selectById(id);
        if (u != null) {
            u.setDelFlag(1);
            userMapper.updateById(u);
        }
        return R.ok();
    }

    /** 重置密码（默认 123456） */
    @PutMapping("/user/{id}/resetPwd")
    @PreAuthorize("hasAuthority('system:user:resetPwd')")
    @OperLog(title = "用户管理", businessType = 2)
    public R<Void> resetPwd(@PathVariable Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");
        u.setPassword(passwordEncoder.encode("123456"));
        userMapper.updateById(u);
        return R.ok();
    }

    /** 启用/停用 */
    @PutMapping("/user/{id}/status")
    @PreAuthorize("hasAuthority('system:user:status')")
    @OperLog(title = "用户管理", businessType = 2)
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (id == 1) throw new BusinessException("不能停用内置管理员");
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");
        u.setStatus("1".equals(body.get("status")) ? 1 : 0);
        userMapper.updateById(u);
        return R.ok();
    }

    /** 分配角色 */
    @PutMapping("/user/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assignRole')")
    @OperLog(title = "用户管理", businessType = 2)
    @Transactional
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userRoleMapper.deleteByUserId(id);
        List<Long> roleIds = body.getOrDefault("roleIds", List.of());
        for (Long rid : roleIds) {
            userRoleMapper.insertUserRole(id, rid);
        }
        return R.ok();
    }

    // ==================== 角色管理 ====================

    @GetMapping("/role/list")
    @PreAuthorize("hasAuthority('system:role:list')")
    public R<List<SysRole>> roleList() {
        return R.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSort)));
    }

    @PostMapping("/role")
    @PreAuthorize("hasAuthority('system:role:add')")
    @OperLog(title = "角色管理", businessType = 1)
    public R<Void> addRole(@RequestBody SysRole role) {
        if (role.getRoleName() == null || role.getRoleKey() == null) throw new BusinessException("参数不完整");
        roleMapper.insert(role);
        return R.ok();
    }

    @PutMapping("/role")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperLog(title = "角色管理", businessType = 2)
    public R<Void> editRole(@RequestBody SysRole role) {
        if (role.getId() == 1) throw new BusinessException("不能修改内置管理员角色");
        roleMapper.updateById(role);
        return R.ok();
    }

    @DeleteMapping("/role/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @OperLog(title = "角色管理", businessType = 3)
    public R<Void> deleteRole(@PathVariable Long id) {
        if (id <= 2) throw new BusinessException("不能删除内置角色");
        roleMapper.deleteById(id);
        return R.ok();
    }

    /** 查询角色已分配的权限 id 列表 */
    @GetMapping("/role/{id}/perms")
    @PreAuthorize("hasAuthority('system:role:assignPerm')")
    public R<List<Long>> rolePerms(@PathVariable Long id) {
        return R.ok(userRoleMapper.selectPermIds(id));
    }

    /** 分配权限 */
    @PutMapping("/role/{id}/perms")
    @PreAuthorize("hasAuthority('system:role:assignPerm')")
    @OperLog(title = "角色管理", businessType = 2)
    @Transactional
    public R<Void> assignPerms(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        if (id == 1) throw new BusinessException("内置管理员拥有全部权限");
        userRoleMapper.deletePermByRoleId(id);
        List<Long> permIds = body.getOrDefault("permIds", List.of());
        if (!permIds.isEmpty()) {
            userRoleMapper.insertRolePerms(id, permIds);
        }
        return R.ok();
    }

    // ==================== 权限管理 ====================

    @GetMapping("/perm/tree")
    @PreAuthorize("hasAuthority('system:perm:list')")
    public R<List<SysPermission>> permTree() {
        return R.ok(permMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSort)));
    }

    @PostMapping("/perm")
    @PreAuthorize("hasAuthority('system:perm:add')")
    @OperLog(title = "权限管理", businessType = 1)
    public R<Void> addPerm(@RequestBody SysPermission perm) {
        permMapper.insert(perm);
        return R.ok();
    }

    @PutMapping("/perm")
    @PreAuthorize("hasAuthority('system:perm:edit')")
    @OperLog(title = "权限管理", businessType = 2)
    public R<Void> editPerm(@RequestBody SysPermission perm) {
        permMapper.updateById(perm);
        return R.ok();
    }

    @DeleteMapping("/perm/{id}")
    @PreAuthorize("hasAuthority('system:perm:delete')")
    @OperLog(title = "权限管理", businessType = 3)
    public R<Void> deletePerm(@PathVariable Long id) {
        permMapper.deleteById(id);
        return R.ok();
    }
}
