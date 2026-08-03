package com.japy.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.common.BusinessException;
import com.japy.module.user.entity.SysUser;
import com.japy.module.user.mapper.SysUserMapper;
import com.japy.module.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 认证查询：按用户名加载用户 + 角色 + 权限 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDelFlag, 0));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        if (user.getStatus() != 0) {
            throw new BusinessException("账号已停用，请联系管理员");
        }
        return new LoginUser(user,
                userRoleMapper.selectRoleKeys(user.getId()),
                userRoleMapper.selectPermKeys(user.getId()));
    }
}
