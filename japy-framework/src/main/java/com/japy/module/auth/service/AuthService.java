package com.japy.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.common.AvatarUtil;
import com.japy.common.BusinessException;
import com.japy.module.auth.dto.LoginDTO;
import com.japy.module.auth.dto.RegisterDTO;
import com.japy.module.auth.vo.TokenVO;
import com.japy.module.system.entity.SysLoginLog;
import com.japy.module.system.mapper.SysLoginLogMapper;
import com.japy.module.user.entity.SysRole;
import com.japy.module.user.entity.SysUser;
import com.japy.module.user.mapper.SysRoleMapper;
import com.japy.module.user.mapper.SysUserMapper;
import com.japy.module.user.mapper.SysUserRoleMapper;
import com.japy.security.JwtUtil;
import com.japy.security.LoginUser;
import com.japy.security.RedisSessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * 认证服务：注册 / 登录（防爆破+登录日志）/ 刷新 / 登出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final RedisSessionService sessionService;

    @Value("${login.max-fail:5}")
    private int maxFail;
    @Value("${login.lock-minutes:30}")
    private int lockMinutes;

    /** 注册（注册即登录，绑定默认 user 角色 + 生成初始头像） */
    @Transactional
    public TokenVO register(RegisterDTO dto, HttpServletRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())
                .eq(SysUser::getDelFlag, 0));
        if (exists > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setAvatar(AvatarUtil.svgDataUri(dto.getNickname(), null));
        user.setStatus(0);
        user.setDelFlag(0);
        userMapper.insert(user);

        // 绑定默认角色（普通用户）
        SysRole defaultRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, "user"));
        if (defaultRole != null) {
            insertUserRole(user.getId(), defaultRole.getId());
        }

        return buildToken(user, request);
    }

    /** 登录：防爆破（账号+IP 维度失败计数）→ 校验密码 → 会话 → 登录日志 */
    public TokenVO login(LoginDTO dto, HttpServletRequest request) {
        String ip = clientIp(request);
        // 防爆破：连续失败锁定（账号+IP 复合，避免攻击者锁定他人账号）
        long failCount = sessionService.incrFailCount(dto.getUsername(), ip, Duration.ofMinutes(lockMinutes));
        if (failCount > maxFail) {
            throw new BusinessException("登录失败次数过多，该账号在此网络环境已锁定 " + lockMinutes + " 分钟");
        }

        try {
            LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(dto.getUsername());
            if (!passwordEncoder.matches(dto.getPassword(), loginUser.getPassword())) {
                saveLoginLog(dto.getUsername(), request, 1, "密码错误");
                throw new BusinessException("密码错误，还可尝试 " + (maxFail - failCount) + " 次");
            }
            sessionService.clearFailCount(dto.getUsername(), ip);
            saveLoginLog(dto.getUsername(), request, 0, "登录成功");
            return buildToken(loginUser.getUser(), request);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            saveLoginLog(dto.getUsername(), request, 1, "用户不存在");
            throw new BusinessException("用户不存在");
        } catch (BusinessException e) {
            saveLoginLog(dto.getUsername(), request, 1, e.getMessage());
            throw e;
        }
    }

    /** 刷新 access token：refresh token 轮换（每次刷新生成新 refresh，旧 refresh 立即失效，防重放） */
    public TokenVO refresh(String refreshToken, HttpServletRequest request) {
        Claims claims = jwtUtil.parse(refreshToken);
        if (claims == null || !"refresh".equals(claims.get("type"))) {
            throw new BusinessException("refresh token 无效或已过期");
        }
        Long userId = jwtUtil.getUserId(claims);
        if (!sessionService.checkRefreshToken(userId, refreshToken)) {
            throw new BusinessException("登录会话已失效，请重新登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 0) {
            throw new BusinessException("账号不可用");
        }
        // 轮换：旧 refresh 被覆盖，重放旧 token 将校验失败
        return buildToken(user, request);
    }

    /** 登出：删除 Redis 会话（token 立即失效） */
    public void logout(Long userId) {
        if (userId != null) {
            sessionService.removeSession(userId);
        }
    }

    /** 当前用户信息 VO（不含敏感字段），供 /auth/info 使用 */
    public com.japy.module.auth.vo.UserInfoVO userInfoVO(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new com.japy.common.BusinessException("用户不存在");
        }
        com.japy.module.auth.vo.UserInfoVO vo = new com.japy.module.auth.vo.UserInfoVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setEmail(u.getEmail());
        vo.setPhone(u.getPhone());
        vo.setSex(u.getSex());
        vo.setCreateTime(u.getCreateTime());
        return vo;
    }

    private TokenVO buildToken(SysUser user, HttpServletRequest request) {
        LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(user.getUsername());
        String access = jwtUtil.createAccessToken(user.getId(), user.getUsername());
        String refresh = jwtUtil.createRefreshToken(user.getId(), user.getUsername());
        sessionService.saveSession(loginUser, refresh, jwtUtil.getAccessExpire() * 2);
        return new TokenVO(access, refresh, jwtUtil.getAccessExpire(),
                user.getId(), user.getNickname(), user.getAvatar(), loginUser.getRoles());
    }

    private void insertUserRole(Long userId, Long roleId) {
        userRoleMapper.insertUserRole(userId, roleId);
    }

    private void saveLoginLog(String username, HttpServletRequest request, int status, String msg) {
        SysLoginLog logEntity = new SysLoginLog();
        logEntity.setUsername(username);
        logEntity.setIpaddr(clientIp(request));
        logEntity.setStatus(status);
        logEntity.setMsg(msg);
        loginLogMapper.insert(logEntity);
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
