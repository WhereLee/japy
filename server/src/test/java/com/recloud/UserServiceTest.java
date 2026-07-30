package com.recloud;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.recloud.common.exception.BizException;
import com.recloud.entity.User;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.UserMapper;
import com.recloud.service.AnnotationFavoriteService;
import com.recloud.service.NotificationService;
import com.recloud.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 *
 * 用 Mockito 模拟依赖，只测业务逻辑，不依赖数据库
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AnnotationMapper annotationMapper;

    @Mock
    private AnnotationFavoriteService annotationFavoriteService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserService userService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯 Mockito 单测无 Spring 容器，需手动初始化 MyBatis-Plus 实体元数据，
        // 否则 LambdaUpdateWrapper/LambdaQueryWrapper 的方法引用解析会报 "can not find lambda cache"
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                User.class
        );
    }

    @Test
    void testGetUserByIdFromCache() throws Exception {
        User user = new User();
        user.setId(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:info:1")).thenReturn("{\"id\":1}");
        when(objectMapper.readValue("{\"id\":1}", User.class)).thenReturn(user);

        User result = userService.getUserById(1L);

        assertEquals(1L, result.getId());
        // 缓存命中，不查库
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void testGetUserByIdFromDb() throws Exception {
        User user = new User();
        user.setId(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:info:1")).thenReturn(null);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(objectMapper.writeValueAsString(user)).thenReturn("{\"id\":1}");

        User result = userService.getUserById(1L);

        assertEquals(1L, result.getId());
        verify(userMapper).selectById(1L);
    }

    @Test
    void testGetUserByIdNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:info:1")).thenReturn(null);
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThrows(BizException.class, () -> userService.getUserById(1L));
    }

    @Test
    void testUpdateStatusBan() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        userService.updateStatus(1L, 0);

        assertEquals(0, user.getStatus());
        // 封禁发送站内通知
        verify(notificationService).sendBanNotification(1L, null);
    }

    @Test
    void testUpdateStatusUnban() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        userService.updateStatus(1L, 1);

        // 解封发送站内通知
        verify(notificationService).sendUnbanNotification(1L);
    }

    @Test
    void testUpdateStatusNotFound() {
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThrows(BizException.class, () -> userService.updateStatus(1L, 0));
    }

    @Test
    void testChangePasswordSuccess() {
        User user = new User();
        user.setId(1L);
        user.setPassword("encodedOld");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("oldPwd", "encodedOld")).thenReturn(true);
        when(passwordEncoder.encode("newPwd")).thenReturn("encodedNew");

        userService.changePassword(1L, "oldPwd", "newPwd");

        verify(passwordEncoder).encode("newPwd");
    }

    @Test
    void testChangePasswordWrongOld() {
        User user = new User();
        user.setId(1L);
        user.setPassword("encodedOld");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrongPwd", "encodedOld")).thenReturn(false);

        assertThrows(BizException.class,
                () -> userService.changePassword(1L, "wrongPwd", "newPwd"));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void testResetPassword() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        String rawPassword = userService.resetPassword(1L);

        assertNotNull(rawPassword);
        assertTrue(rawPassword.endsWith("!Aa1"));
        verify(notificationService).sendPasswordResetNotification(1L);
    }

    @Test
    void testResetPasswordNotFound() {
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThrows(BizException.class, () -> userService.resetPassword(1L));
    }
}
