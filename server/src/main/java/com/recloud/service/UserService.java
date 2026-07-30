package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.entity.Annotation;
import com.recloud.entity.User;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.UserMapper;
import com.recloud.vo.AnnotationVO;
import com.recloud.vo.UserProfileVO;
import com.recloud.vo.VOConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务
 * <p>
 * 缓存策略：Redis 缓存用户信息，TTL = 30min
 * - 读：Redis → DB → 回填 Redis
 * - 写：DB → 删 Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final AnnotationMapper annotationMapper;
    private final AnnotationFavoriteService annotationFavoriteService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    private static final String CACHE_PREFIX = "user:info:";
    private static final String DISABLED_PREFIX = "user:disabled:";
    private static final long CACHE_TTL_MINUTES = 30;

    /**
     * 根据 ID 查询用户（Redis 缓存）
     */
    public User getUserById(Long userId) {
        String cacheKey = CACHE_PREFIX + userId;

        // 1. 查 Redis
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return objectMapper.readValue(json, User.class);
            }
        } catch (Exception e) {
            log.warn("Redis 查询用户缓存失败: {}", e.getMessage());
        }

        // 2. 查 DB
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 回填 Redis
        try {
            String json = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 回填用户缓存失败: {}", e.getMessage());
        }

        return user;
    }

    /**
     * 根据用户名查询用户
     */
    public User getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
    }

    /**
     * 修改昵称（写操作 → 删缓存）
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateNickname(Long userId, String nickname) {
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getNickname, nickname)
        );
        // 删缓存
        try {
            redisTemplate.delete(CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("删除用户缓存失败: {}", e.getMessage());
        }
        return userMapper.selectById(userId);
    }

    /**
     * 查询所有用户（管理端用）
     */
    public List<User> listUsers() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getId)
        );
    }

    /**
     * 新增用户（注册时调用）
     */
    public User createUser(User user) {
        userMapper.insert(user);
        return user;
    }

    /**
     * 管理员分页查询用户（支持关键词搜索）
     */
    public IPage<User> listUsers(int page, int size, String keyword) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 管理员修改用户状态（启用/禁用）
     * 禁用时写入 Redis 标记，JWT Filter 实时拦截（秒级生效）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        user.setStatus(status);
        userMapper.updateById(user);
        // 清除缓存
        try {
            redisTemplate.delete(CACHE_PREFIX + userId);
            // 禁用时写入实时拦截标记（TTL 7天，与 refresh token 对齐）
            if (status != null && status == 0) {
                redisTemplate.opsForValue().set(DISABLED_PREFIX + userId, "1", 7, TimeUnit.DAYS);
            } else {
                redisTemplate.delete(DISABLED_PREFIX + userId);
            }
        } catch (Exception e) {
            log.warn("删除用户缓存失败: {}", e.getMessage());
        }
        // 发送站内通知（封禁/解封）
        if (status != null && status == 0) {
            notificationService.sendBanNotification(userId, null);
        } else {
            notificationService.sendUnbanNotification(userId);
        }
    }

    /**
     * 用户修改密码（需验证旧密码）
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        // 显式 select password 字段（@TableField(select=false) 导致 selectById 不查密码）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getPassword)
                        .eq(User::getId, userId)
        );
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        // 更新密码
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getPassword, passwordEncoder.encode(newPassword))
        );
        // 清除缓存
        try {
            redisTemplate.delete(CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("删除用户缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 管理员重置用户密码（生成随机密码）
     */
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        // 生成随机密码：UUID 前8位 + 特殊字符
        String rawPassword = java.util.UUID.randomUUID().toString().substring(0, 8) + "!Aa1";
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getPassword, passwordEncoder.encode(rawPassword))
        );
        // 清除缓存（密码变更后旧缓存可能包含敏感信息）
        try {
            redisTemplate.delete(CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("删除用户缓存失败: {}", e.getMessage());
        }
        // 发送密码重置通知
        notificationService.sendPasswordResetNotification(userId);
        return rawPassword;
    }

    /**
     * 获取用户个人主页（聚合统计）
     * <p>
     * 聚合内容：
     * - 基本信息（昵称、角色、注册时间）
     * - 社区贡献（批注数、获赞总数、获评论总数）
     * - 收藏数
     * - 最近 10 条批注
     */
    public UserProfileVO getUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }

        UserProfileVO profile = new UserProfileVO();
        profile.setId(user.getId());
        profile.setNickname(user.getNickname());
        profile.setRole(user.getRole());
        profile.setRegisteredAt(user.getCreatedAt());

        // 查询该用户所有批注
        List<Annotation> annotations = annotationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getUserId, userId)
                        .orderByDesc(Annotation::getCreatedAt)
        );

        // 统计获赞总数、获评论总数
        long totalLikes = 0;
        long totalComments = 0;
        for (Annotation a : annotations) {
            totalLikes += (a.getLikeCount() != null ? a.getLikeCount() : 0);
            totalComments += (a.getCommentCount() != null ? a.getCommentCount() : 0);
        }

        profile.setAnnotationCount(annotations.size());
        profile.setTotalLikesReceived(totalLikes);
        profile.setTotalCommentsReceived(totalComments);
        profile.setFavoriteCount(annotationFavoriteService.countByUser(userId));

        // 最近 10 条批注
        List<Annotation> recent = annotations.stream().limit(10).toList();
        profile.setRecentAnnotations(VOConverter.toAnnotationVOList(recent));

        return profile;
    }
}
