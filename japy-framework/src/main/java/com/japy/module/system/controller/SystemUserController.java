package com.japy.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.aspect.Idempotent;
import com.japy.aspect.OperLog;
import com.japy.common.BusinessException;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.module.system.dto.AdminDtos;
import com.japy.module.system.dto.UserDtos;
import com.japy.module.user.entity.SysPermission;
import com.japy.module.user.entity.SysRole;
import com.japy.module.user.entity.SysUser;
import com.japy.module.user.mapper.SysPermissionMapper;
import com.japy.module.user.mapper.SysRoleMapper;
import com.japy.module.user.mapper.SysUserMapper;
import com.japy.module.user.mapper.SysUserRoleMapper;
import com.japy.security.RedisSessionService;
import jakarta.validation.Valid;
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
    private final RedisSessionService sessionService;

    // ==================== 用户管理 ====================

    @GetMapping("/user/list")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
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
    @PreAuthorize("@ss.hasPermi('system:user:add')")
    @OperLog(title = "用户管理", businessType = 1)
    @Idempotent
    public R<Void> addUser(@Valid @RequestBody UserDtos.AddDTO dto) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()).eq(SysUser::getDelFlag, 0));
        if (exists > 0) throw new BusinessException("用户名已存在");
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setSex(dto.getSex());
        user.setStatus(0);
        user.setDelFlag(0);
        userMapper.insert(user);
        return R.ok();
    }

    @PutMapping("/user")
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @OperLog(title = "用户管理", businessType = 2)
    public R<Void> editUser(@Valid @RequestBody UserDtos.EditDTO dto) {
        SysUser db = userMapper.selectById(dto.getId());
        if (db == null) throw new BusinessException("用户不存在");
        db.setNickname(dto.getNickname());
        db.setEmail(dto.getEmail());
        db.setPhone(dto.getPhone());
        db.setSex(dto.getSex());
        userMapper.updateById(db);
        return R.ok();
    }

    @DeleteMapping("/user/{id}")
    @PreAuthorize("@ss.hasPermi('system:user:delete')")
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
    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
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
    @PreAuthorize("@ss.hasPermi('system:user:status')")
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
    @PreAuthorize("@ss.hasPermi('system:user:assignRole')")
    @OperLog(title = "用户管理", businessType = 2)
    @Transactional
    public R<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody UserDtos.AssignRoleDTO dto) {
        userRoleMapper.deleteByUserId(id);
        List<Long> roleIds = dto.getRoleIds() == null ? List.of() : dto.getRoleIds();
        for (Long rid : roleIds) {
            userRoleMapper.insertUserRole(id, rid);
        }
        // 角色变更：该用户旧会话权限失效，强制重新登录
        sessionService.removeSession(id);
        return R.ok();
    }

    // ==================== 角色管理 ====================

    @GetMapping("/role/list")
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    public R<List<SysRole>> roleList() {
        return R.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSort)));
    }

    @PostMapping("/role")
    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @OperLog(title = "角色管理", businessType = 1)
    @Idempotent
    public R<Void> addRole(@Valid @RequestBody AdminDtos.RoleDTO dto) {
        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setSort(dto.getSort() == null ? 0 : dto.getSort());
        role.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        role.setRemark(dto.getRemark());
        roleMapper.insert(role);
        return R.ok();
    }

    @PutMapping("/role")
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @OperLog(title = "角色管理", businessType = 2)
    public R<Void> editRole(@Valid @RequestBody AdminDtos.RoleDTO dto) {
        if (dto.getId() == null || dto.getId() == 1) throw new BusinessException("不能修改内置管理员角色");
        SysRole role = new SysRole();
        role.setId(dto.getId());
        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setSort(dto.getSort());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        roleMapper.updateById(role);
        return R.ok();
    }

    @DeleteMapping("/role/{id}")
    @PreAuthorize("@ss.hasPermi('system:role:delete')")
    @OperLog(title = "角色管理", businessType = 3)
    public R<Void> deleteRole(@PathVariable Long id) {
        if (id <= 2) throw new BusinessException("不能删除内置角色");
        roleMapper.deleteById(id);
        return R.ok();
    }

    /** 查询角色已分配的权限 id 列表 */
    @GetMapping("/role/{id}/perms")
    @PreAuthorize("@ss.hasPermi('system:role:assignPerm')")
    public R<List<Long>> rolePerms(@PathVariable Long id) {
        return R.ok(userRoleMapper.selectPermIds(id));
    }

    /** 分配权限 */
    @PutMapping("/role/{id}/perms")
    @PreAuthorize("@ss.hasPermi('system:role:assignPerm')")
    @OperLog(title = "角色管理", businessType = 2)
    @Transactional
    public R<Void> assignPerms(@PathVariable Long id, @Valid @RequestBody AdminDtos.AssignPermDTO dto) {
        if (id == 1) throw new BusinessException("内置管理员拥有全部权限");
        userRoleMapper.deletePermByRoleId(id);
        List<Long> permIds = dto.getPermIds() == null ? List.of() : dto.getPermIds();
        if (!permIds.isEmpty()) {
            userRoleMapper.insertRolePerms(id, permIds);
        }
        // 权限变更：该角色下所有在线用户会话失效（旧权限立即作废）
        for (Long uid : userRoleMapper.selectUserIdsByRole(id)) {
            sessionService.removeSession(uid);
        }
        return R.ok();
    }

    // ==================== 权限管理 ====================

    @GetMapping("/perm/tree")
    @PreAuthorize("@ss.hasPermi('system:perm:list')")
    public R<List<SysPermission>> permTree() {
        return R.ok(permMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSort)));
    }

    @PostMapping("/perm")
    @PreAuthorize("@ss.hasPermi('system:perm:add')")
    @OperLog(title = "权限管理", businessType = 1)
    public R<Void> addPerm(@RequestBody SysPermission perm) {
        permMapper.insert(perm);
        return R.ok();
    }

    @PutMapping("/perm")
    @PreAuthorize("@ss.hasPermi('system:perm:edit')")
    @OperLog(title = "权限管理", businessType = 2)
    public R<Void> editPerm(@RequestBody SysPermission perm) {
        permMapper.updateById(perm);
        return R.ok();
    }

    @DeleteMapping("/perm/{id}")
    @PreAuthorize("@ss.hasPermi('system:perm:delete')")
    @OperLog(title = "权限管理", businessType = 3)
    public R<Void> deletePerm(@PathVariable Long id) {
        permMapper.deleteById(id);
        return R.ok();
    }
}
