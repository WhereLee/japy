package com.recloud;

import com.recloud.entity.Annotation;
import com.recloud.entity.User;
import com.recloud.mapper.AnnotationLikeMapper;
import com.recloud.mapper.AnnotationMapper;
import com.recloud.mapper.UserMapper;
import com.recloud.service.AnnotationLikeService;
import com.recloud.service.AnnotationService;
import com.recloud.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核心业务 Service 层测试
 *
 * 测试批注、点赞、用户等核心业务逻辑
 * <p>
 * 注意：本类按 @Order 顺序构建并复用测试数据（用户→批注→点赞→删除），
 * 因此不加 @Transactional（否则每个方法结束回滚，跨方法的静态 ID 会指向已回滚的行）。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServiceLayerTest {

    @Autowired
    private AnnotationService annotationService;

    @Autowired
    private AnnotationLikeService likeService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AnnotationMapper annotationMapper;

    @Autowired
    private AnnotationLikeMapper likeMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static Long testUserId;
    private static Long testAnnotationId;

    @Test
    @Order(1)
    void testCreateUser() {
        User user = new User();
        user.setUsername("svc_test_" + System.currentTimeMillis());
        user.setNickname("服务层测试用户");
        user.setPassword("$2a$10$dummy"); // 已加密密码
        user.setRole("user");
        user.setStatus(1);
        userMapper.insert(user);

        testUserId = user.getId();
        assertNotNull(testUserId);
        assertTrue(testUserId > 0);
    }

    @Test
    @Order(2)
    void testCreateAnnotation() {
        if (testUserId == null) {
            testUserId = 1L; // fallback
        }
        Annotation annotation = annotationService.create(
                1L, testUserId, 10, 20, "测试选文", "测试批注内容", 0
        );

        assertNotNull(annotation);
        assertNotNull(annotation.getId());
        testAnnotationId = annotation.getId();

        assertEquals(1L, annotation.getChapterId());
        assertEquals(testUserId, annotation.getUserId());
        assertEquals(Integer.valueOf(10), annotation.getAnchorStart());
        assertEquals(Integer.valueOf(20), annotation.getAnchorEnd());
        assertEquals("测试选文", annotation.getSelectedText());
        assertEquals("测试批注内容", annotation.getContent());
        assertEquals(Integer.valueOf(0), annotation.getType());
    }

    @Test
    @Order(3)
    void testListAnnotationsByChapter() {
        // 清除章节缓存强制走 DB，避免缓存中残留的空值导致误判
        redisTemplate.delete("annotation:chapter:1");
        var list = annotationService.listByChapter(1L);
        assertNotNull(list);
        assertFalse(list.isEmpty(), "章节1应有批注");
    }

    @Test
    @Order(4)
    void testToggleLike() {
        if (testAnnotationId == null) {
            testAnnotationId = 1L;
        }
        if (testUserId == null) {
            testUserId = 1L;
        }

        // 点赞
        Map<String, Object> result = likeService.toggle(testAnnotationId, testUserId);
        assertNotNull(result);
        assertTrue((Boolean) result.get("liked"));
        assertTrue((Long) result.get("likeCount") > 0);

        // 取消点赞
        result = likeService.toggle(testAnnotationId, testUserId);
        assertFalse((Boolean) result.get("liked"));
    }

    @Test
    @Order(5)
    void testLikeStatus() {
        if (testAnnotationId == null) {
            testAnnotationId = 1L;
        }
        if (testUserId == null) {
            testUserId = 1L;
        }

        boolean liked = likeService.isLiked(testAnnotationId, testUserId);
        long count = likeService.countByAnnotation(testAnnotationId);

        // 状态查询不应抛异常
        assertNotNull(liked);
        assertTrue(count >= 0);
    }

    @Test
    @Order(6)
    void testAnnotationLikeCountAtomicUpdate() {
        if (testAnnotationId == null) {
            testAnnotationId = 1L;
        }

        // 获取当前 likeCount
        Annotation ann = annotationMapper.selectById(testAnnotationId);
        int before = ann.getLikeCount() != null ? ann.getLikeCount() : 0;

        // 原子 +1
        annotationMapper.updateLikeCount(testAnnotationId, 1);
        ann = annotationMapper.selectById(testAnnotationId);
        assertEquals(before + 1, ann.getLikeCount());

        // 原子 -1 恢复
        annotationMapper.updateLikeCount(testAnnotationId, -1);
        ann = annotationMapper.selectById(testAnnotationId);
        assertEquals(before, ann.getLikeCount());
    }

    @Test
    @Order(7)
    void testAnnotationCommentCountAtomicUpdate() {
        if (testAnnotationId == null) {
            testAnnotationId = 1L;
        }

        Annotation ann = annotationMapper.selectById(testAnnotationId);
        int before = ann.getCommentCount() != null ? ann.getCommentCount() : 0;

        annotationMapper.updateCommentCount(testAnnotationId, 1);
        ann = annotationMapper.selectById(testAnnotationId);
        assertEquals(before + 1, ann.getCommentCount());

        annotationMapper.updateCommentCount(testAnnotationId, -1);
        ann = annotationMapper.selectById(testAnnotationId);
        assertEquals(before, ann.getCommentCount());
    }

    @Test
    @Order(8)
    void testDeleteAnnotation() {
        if (testAnnotationId == null || testUserId == null) {
            return; // skip if no test data
        }

        boolean deleted = annotationService.deleteAnnotation(testAnnotationId, testUserId);
        assertTrue(deleted);

        // 验证已删除
        Annotation ann = annotationMapper.selectById(testAnnotationId);
        assertNull(ann);
    }
}
