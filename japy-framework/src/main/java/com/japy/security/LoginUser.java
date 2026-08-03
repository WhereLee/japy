package com.japy.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.japy.module.user.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * 登录用户（UserDetails 实现）：持有用户信息 + 角色/权限集合。
 * 存入 Redis 会话需可序列化（Lombok 生成无参/全参构造与 getter/setter）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements UserDetails {

    private SysUser user;
    private List<String> roles;
    private List<String> perms;

    public Long getUserId() { return user.getId(); }

    public String getNickname() { return user.getNickname(); }

    /** 反序列化时忽略（GrantedAuthority 为接口，由 roles/perms 动态构造） */
    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 权限以 ROLE_ 前缀标识角色，普通字符串标识按钮权限
        return Stream.concat(
                roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())),
                perms.stream().map(SimpleGrantedAuthority::new)
        ).toList();
    }

    @Override
    public String getPassword() { return user.getPassword(); }

    @Override
    public String getUsername() { return user.getUsername(); }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return user.getStatus() == 0; }
}
