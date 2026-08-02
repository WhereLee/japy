package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 个人主页与通知模块测试：公开主页/资料修改/密码修改/通知已读链路。
 */
class ProfileNotificationTest extends TestBase {

    private static final String PREFIX = "t_prof_";
    private static String tokenA, tokenB;
    private static Long userIdA, momentId;

    @Test @Order(1)
    void 准备数据() throws Exception {
        tokenA = registerOrLogin(PREFIX + "a", "pass123", "主页用户A");
        tokenB = registerOrLogin(PREFIX + "b", "pass123", "主页用户B");
        // 记录 A 的 userId：从登录接口返回
        MvcResult login = postJson("/auth/login", null,
                "{\"username\":\"" + PREFIX + "a\",\"password\":\"pass123\"}");
        userIdA = body(login).get("data").get("userId").asLong();
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"主页展示动态\"}");
        momentId = body(r).get("data").get("id").asLong();
    }

    // ===== 公开主页 =====

    @Test @Order(10)
    void 公开主页返回用户信息与动态() throws Exception {
        MvcResult r = getReq("/api/users/" + userIdA, null);
        JsonNode node = body(r);
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("nickname").asText().length() > 0, "主页应返回昵称");
        assertTrue(node.get("data").get("moments").get("total").asLong() >= 1, "主页应含动态");
    }

    @Test @Order(11)
    void 主页不泄露密码字段() throws Exception {
        JsonNode node = body(getReq("/api/users/" + userIdA, null));
        assertTrue(node.get("data").get("password") == null, "公开主页不应包含密码");
        assertTrue(node.get("data").get("username") == null, "公开主页不应包含用户名");
    }

    @Test @Order(12)
    void 不存在的用户被拒() throws Exception {
        assertEquals(400, body(getReq("/api/users/999999", null)).get("code").asInt());
    }

    // ===== 资料修改 =====

    @Test @Order(20)
    void 修改昵称成功并重签token() throws Exception {
        MvcResult r = putJson("/api/users/me", tokenA, "{\"nickname\":\"新昵称A\"}");
        JsonNode node = body(r);
        assertEquals(200, node.get("code").asInt());
        assertEquals("新昵称A", node.get("data").get("nickname").asText());
        assertNotNull(node.get("data").get("token"), "改昵称后应重签 token");
        tokenA = node.get("data").get("token").asText();
    }

    @Test @Order(21)
    void 昵称超50字被拒() throws Exception {
        String longNick = "n".repeat(51);
        assertEquals(400, body(putJson("/api/users/me", tokenA,
                "{\"nickname\":\"" + longNick + "\"}")).get("code").asInt());
    }

    @Test @Order(22)
    void 简介超200字被拒() throws Exception {
        String longBio = "b".repeat(201);
        assertEquals(400, body(putJson("/api/users/me", tokenA,
                "{\"bio\":\"" + longBio + "\"}")).get("code").asInt());
    }

    @Test @Order(23)
    void 修改头像成功() throws Exception {
        assertEquals(200, body(putJson("/api/users/me", tokenA,
                "{\"avatar\":\"http://example.com/avatar.png\"}")).get("code").asInt());
        JsonNode node = body(getReq("/api/users/" + userIdA, null));
        assertEquals("http://example.com/avatar.png", node.get("data").get("avatar").asText());
    }

    // ===== 密码修改 =====

    @Test @Order(30)
    void 修改密码成功后新密码可登录() throws Exception {
        // 用时间戳用户名，保证每次运行独立（避免上次运行改过的密码残留）
        String user = PREFIX + "pwdchg" + System.currentTimeMillis();
        String tmpToken = registerOrLogin(user, "oldpass1", "改密用户");
        assertNotNull(tmpToken, "注册应成功");
        assertEquals(200, body(putJson("/api/users/me/password", tmpToken,
                "{\"oldPassword\":\"oldpass1\",\"newPassword\":\"newpass1\"}")).get("code").asInt());
        String newToken = login(user, "newpass1");
        assertNotNull(newToken, "新密码应可登录");
    }

    @Test @Order(31)
    void 错误旧密码被拒() throws Exception {
        String user = PREFIX + "wrongold";
        String tmpToken = registerOrLogin(user, "pass123", "错旧密用户");
        assertEquals(400, body(putJson("/api/users/me/password", tmpToken,
                "{\"oldPassword\":\"wrong\",\"newPassword\":\"newpass1\"}")).get("code").asInt());
    }

    @Test @Order(32)
    void 新密码不足6位被拒() throws Exception {
        String user = PREFIX + "shortnew";
        String tmpToken = registerOrLogin(user, "pass123", "短新密用户");
        assertEquals(400, body(putJson("/api/users/me/password", tmpToken,
                "{\"oldPassword\":\"pass123\",\"newPassword\":\"123\"}")).get("code").asInt());
    }

    // ===== 我的内容 =====

    @Test @Order(40)
    void 我的动态列表() throws Exception {
        JsonNode node = body(getReq("/api/users/me/moments?page=1&size=20", tokenA));
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asLong() >= 1);
    }

    @Test @Order(41)
    void 我的评论列表() throws Exception {
        JsonNode node = body(getReq("/api/users/me/comments?page=1&size=20", tokenB));
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asLong() >= 0);
    }

    // ===== 通知链路 =====

    @Test @Order(50)
    void B评论A后A收到通知() throws Exception {
        postJson("/api/comments", tokenB, "{\"momentId\":" + momentId + ",\"content\":\"通知测试评论\"}");
        JsonNode node = body(getReq("/api/notifications?page=1&size=20", tokenA));
        boolean found = false;
        for (JsonNode n : node.get("data").get("list")) {
            if ("comment".equals(n.get("type").asText()) && n.get("refId").asLong() == momentId) {
                found = true;
            }
        }
        assertTrue(found, "A 应收到 B 评论其动态的通知");
    }

    @Test @Order(51)
    void 未读数统计正确() throws Exception {
        JsonNode before = body(getReq("/api/notifications/unread-count", tokenA));
        // 刚才 B 的评论使 A 有未读
        assertTrue(before.get("data").get("count").asLong() >= 1);
    }

    @Test @Order(52)
    void 单条已读() throws Exception {
        JsonNode list = body(getReq("/api/notifications?page=1&size=20", tokenA)).get("data").get("list");
        long unreadId = -1;
        for (JsonNode n : list) {
            if (n.get("isRead").asInt() == 0) { unreadId = n.get("id").asLong(); break; }
        }
        if (unreadId > 0) {
            assertEquals(200, body(putJson("/api/notifications/" + unreadId + "/read", tokenA, null)).get("code").asInt());
        }
    }

    @Test @Order(53)
    void 全部已读后未读归零() throws Exception {
        assertEquals(200, body(putJson("/api/notifications/read-all", tokenA, null)).get("code").asInt());
        JsonNode count = body(getReq("/api/notifications/unread-count", tokenA));
        assertEquals(0, count.get("data").get("count").asLong());
    }

    @Test @Order(54)
    void 自赞自评不产生通知() throws Exception {
        // A 点赞/评论自己的动态，不应产生给 A 的通知
        postJson("/api/moments/" + momentId + "/like", tokenA, null);
        postJson("/api/comments", tokenA, "{\"momentId\":" + momentId + ",\"content\":\"自评\"}");
        JsonNode node = body(getReq("/api/notifications?page=1&size=50", tokenA));
        for (JsonNode n : node.get("data").get("list")) {
            assertFalse("like".equals(n.get("type").asText()) && n.get("refId").asLong() == momentId,
                    "自赞不应产生通知");
            assertFalse("comment".equals(n.get("type").asText()) && n.get("refId").asLong() == momentId
                            && n.get("content").asText().contains("自评"),
                    "自评不应产生通知");
        }
    }

    @Test @Order(55)
    void 不能读别人的通知() throws Exception {
        // 先生成一条 A 的未读通知（B 再评论一次）
        postJson("/api/comments", tokenB, "{\"momentId\":" + momentId + ",\"content\":\"未读通知测试\"}");
        JsonNode list = body(getReq("/api/notifications?page=1&size=20", tokenA)).get("data").get("list");
        long unreadId = -1;
        for (JsonNode n : list) {
            if (n.get("isRead").asInt() == 0) { unreadId = n.get("id").asLong(); break; }
        }
        assertTrue(unreadId > 0, "A 应有未读通知");
        // B 尝试把 A 的通知标为已读 → 无权限
        putJson("/api/notifications/" + unreadId + "/read", tokenB, null);
        JsonNode after = body(getReq("/api/notifications?page=1&size=20", tokenA)).get("data").get("list");
        for (JsonNode n : after) {
            if (n.get("id").asLong() == unreadId) {
                assertEquals(0, n.get("isRead").asInt(), "他人不能标记我的通知为已读");
            }
        }
    }

    @Test @Order(60)
    void 清理动态() throws Exception {
        deleteReq("/api/moments/" + momentId, tokenA);
    }
}
