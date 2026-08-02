package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 举报与管理端测试：举报边界/封禁/重置密码/强改昵称/隐藏恢复/举报处理/敏感词/公告/日志/权限。
 */
class ReportAdminTest extends TestBase {

    private static final String PREFIX = "t_admin_";
    private static String tokenU, tokenAdmin;
    private static Long momentId, commentId, reportId;

    @Test @Order(1)
    void 准备数据() throws Exception {
        tokenU = registerOrLogin(PREFIX + "u", "pass123", "被管用户");
        tokenAdmin = adminToken();
        assertNotNull(tokenAdmin);
    }

    @Test @Order(2)
    void 普通用户访问管理端返回403() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/dashboard")
                        .header("Authorization", bearer(tokenU)))
                .andExpect(status().isForbidden());
    }

    // ===== 举报 =====

    @Test @Order(10)
    void 准备举报素材() throws Exception {
        MvcResult r = postJson("/api/moments", tokenU, "{\"content\":\"待举报动态\"}");
        momentId = body(r).get("data").get("id").asLong();
        MvcResult c = postJson("/api/comments", tokenU, "{\"momentId\":" + momentId + ",\"content\":\"待举报评论\"}");
        commentId = body(c).get("data").get("id").asLong();
    }

    @Test @Order(11)
    void 举报动态成功() throws Exception {
        MvcResult r = postJson("/api/reports", tokenAdmin,
                "{\"targetType\":\"moment\",\"targetId\":" + momentId + ",\"reason\":\"测试举报动态\"}");
        assertEquals(200, body(r).get("code").asInt());
        reportId = body(r).get("data").get("id").asLong();
    }

    @Test @Order(12)
    void 重复举报被拒() throws Exception {
        assertEquals(400, body(postJson("/api/reports", tokenAdmin,
                "{\"targetType\":\"moment\",\"targetId\":" + momentId + ",\"reason\":\"重复\"}")).get("code").asInt());
    }

    @Test @Order(13)
    void 举报自己的内容被拒() throws Exception {
        assertEquals(400, body(postJson("/api/reports", tokenU,
                "{\"targetType\":\"moment\",\"targetId\":" + momentId + ",\"reason\":\"自举\"}")).get("code").asInt());
    }

    @Test @Order(14)
    void 非法举报类型被拒() throws Exception {
        assertEquals(400, body(postJson("/api/reports", tokenAdmin,
                "{\"targetType\":\"xxx\",\"targetId\":1,\"reason\":\"非法类型\"}")).get("code").asInt());
    }

    @Test @Order(15)
    void 举报不存在的对象被拒() throws Exception {
        assertEquals(400, body(postJson("/api/reports", tokenAdmin,
                "{\"targetType\":\"moment\",\"targetId\":999999,\"reason\":\"不存在\"}")).get("code").asInt());
    }

    @Test @Order(16)
    void 我的举报列表() throws Exception {
        JsonNode node = body(getReq("/api/reports/my?page=1&size=20", tokenAdmin));
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asLong() >= 1);
    }

    // ===== 管理端：用户管理 =====

    @Test @Order(20)
    void 用户列表不泄露密码() throws Exception {
        JsonNode node = body(getReq("/api/admin/users?page=1&size=50", tokenAdmin));
        assertEquals(200, node.get("code").asInt());
        for (JsonNode u : node.get("data").get("list")) {
            assertTrue(u.get("password") == null || u.get("password").isNull(), "用户列表不应返回密码");
        }
    }

    @Test @Order(21)
    void 封禁后用户请求被拒() throws Exception {
        var u = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.japy.entity.User>()
                        .eq(com.japy.entity.User::getUsername, PREFIX + "u"));
        assertNotNull(u, "被封禁用户应存在");
        assertEquals(200, body(putJson("/api/admin/users/" + u.getId() + "/ban", tokenAdmin,
                "{\"reason\":\"测试封禁\"}")).get("code").asInt());
        // 被封禁用户访问受保护接口 → 403
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/moments")
                        .header("Authorization", bearer(tokenU))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"封禁后发动态\"}"))
                .andExpect(status().isForbidden());
    }

    @Test @Order(22)
    void 解封后用户恢复() throws Exception {
        var u = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.japy.entity.User>()
                        .eq(com.japy.entity.User::getUsername, PREFIX + "u"));
        assertEquals(200, body(putJson("/api/admin/users/" + u.getId() + "/unban", tokenAdmin, null)).get("code").asInt());
        assertEquals(200, body(postJson("/api/moments", tokenU, "{\"content\":\"解封后发动态\"}")).get("code").asInt(),
                "解封后应可正常操作");
    }

    @Test @Order(23)
    void 重置密码后新密码可登录() throws Exception {
        String user = PREFIX + "resetpwd" + System.currentTimeMillis();
        registerOrLogin(user, "pass123", "重置密码用户");
        var u = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.japy.entity.User>()
                        .eq(com.japy.entity.User::getUsername, user));
        assertNotNull(u, "用户应先注册成功");
        assertEquals(200, body(putJson("/api/admin/users/" + u.getId() + "/reset-password", tokenAdmin, null)).get("code").asInt());
        String newToken = login(user, "123456");
        assertNotNull(newToken, "重置后的默认密码 123456 应可登录");
    }

    @Test @Order(24)
    void 强制改名后新昵称生效() throws Exception {
        var u = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.japy.entity.User>()
                        .eq(com.japy.entity.User::getUsername, PREFIX + "renamed"));
        String tmpToken = registerOrLogin(PREFIX + "renamed", "pass123", "原名");
        assertEquals(200, body(putJson("/api/admin/users/" + u.getId() + "/nickname", tokenAdmin,
                "{\"nickname\":\"被强改昵称\"}")).get("code").asInt());
        // 拦截器从库中取最新昵称 → 旧 token 发动态也显示新昵称
        MvcResult r = postJson("/api/moments", tmpToken, "{\"content\":\"强改昵称后发布\"}");
        assertEquals("被强改昵称", body(r).get("data").get("nickname").asText());
    }

    @Test @Order(25)
    void 不能封禁自己() throws Exception {
        var admin = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.japy.entity.User>()
                        .eq(com.japy.entity.User::getUsername, "t_admin_master"));
        assertEquals(400, body(putJson("/api/admin/users/" + admin.getId() + "/ban", tokenAdmin,
                "{\"reason\":\"自封\"}")).get("code").asInt());
    }

    // ===== 管理端：动态/评论管理 =====

    @Test @Order(30)
    void 管理端动态列表() throws Exception {
        JsonNode node = body(getReq("/api/admin/moments?page=1&size=20", tokenAdmin));
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asLong() >= 1);
    }

    @Test @Order(31)
    void 管理端删除动态() throws Exception {
        MvcResult r = postJson("/api/moments", tokenU, "{\"content\":\"将被管理员删除\"}");
        long id = body(r).get("data").get("id").asLong();
        assertEquals(200, body(deleteReq("/api/admin/moments/" + id, tokenAdmin)).get("code").asInt());
        // 时间线不可见
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            assertNotEquals(id, m.get("id").asLong());
        }
    }

    @Test @Order(32)
    void 隐藏评论后列表不可见() throws Exception {
        assertEquals(200, body(putJson("/api/admin/comments/" + commentId + "/hide", tokenAdmin, null)).get("code").asInt());
        JsonNode node = body(getReq("/api/comments?momentId=" + momentId + "&page=1&size=20", null));
        assertEquals(0, node.get("data").get("total").asLong(), "被隐藏的评论不应出现在列表");
    }

    @Test @Order(33)
    void 恢复评论() throws Exception {
        assertEquals(200, body(putJson("/api/admin/comments/" + commentId + "/restore", tokenAdmin, null)).get("code").asInt());
        JsonNode node = body(getReq("/api/comments?momentId=" + momentId + "&page=1&size=20", null));
        assertEquals(1, node.get("data").get("total").asLong());
    }

    // ===== 管理端：举报处理 =====

    @Test @Order(40)
    void 处理举报后内容被隐藏且举报者收到通知() throws Exception {
        assertEquals(200, body(putJson("/api/admin/reports/" + reportId + "/resolve", tokenAdmin,
                "{\"result\":\"内容违规已隐藏\"}")).get("code").asInt());
        // 被举报动态被隐藏
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            assertNotEquals(momentId, m.get("id").asLong(), "举报处理后动态应被隐藏");
        }
        // 举报者（admin）收到结果通知
        JsonNode notifs = body(getReq("/api/notifications?page=1&size=50", tokenAdmin)).get("data").get("list");
        boolean found = false;
        for (JsonNode n : notifs) {
            if ("report_result".equals(n.get("type").asText())) found = true;
        }
        assertTrue(found, "举报者应收到处理结果通知");
    }

    @Test @Order(41)
    void 重复处理举报被拒() throws Exception {
        assertEquals(400, body(putJson("/api/admin/reports/" + reportId + "/resolve", tokenAdmin,
                "{\"result\":\"再处理\"}")).get("code").asInt());
    }

    @Test @Order(42)
    void 驳回举报() throws Exception {
        MvcResult r = postJson("/api/reports", tokenAdmin,
                "{\"targetType\":\"comment\",\"targetId\":" + commentId + ",\"reason\":\"待驳回\"}");
        long id = body(r).get("data").get("id").asLong();
        assertEquals(200, body(putJson("/api/admin/reports/" + id + "/reject", tokenAdmin, null)).get("code").asInt());
    }

    // ===== 管理端：敏感词 =====

    @Test @Order(50)
    void 添加敏感词后立即生效() throws Exception {
        String word = "敏感词_zzz_admin_" + System.currentTimeMillis();
        assertEquals(200, body(postJson("/api/admin/sensitive-words", tokenAdmin,
                "{\"word\":\"" + word + "\"}")).get("code").asInt());
        // 立即发含该词的动态被拒（缓存失效生效）
        assertEquals(400, body(postJson("/api/moments", tokenU,
                "{\"content\":\"包含" + word + "的内容\"}")).get("code").asInt());
        // 清理：删除该词
        JsonNode words = body(getReq("/api/admin/sensitive-words", tokenAdmin)).get("data");
        for (JsonNode w : words) {
            if (word.equals(w.get("word").asText())) {
                assertEquals(200, body(deleteReq("/api/admin/sensitive-words/" + w.get("id").asLong(), tokenAdmin)).get("code").asInt());
            }
        }
        // 删除后恢复可发
        assertEquals(200, body(postJson("/api/moments", tokenU,
                "{\"content\":\"包含" + word + "的内容\"}")).get("code").asInt());
    }

    @Test @Order(51)
    void 重复添加敏感词被拒() throws Exception {
        String word = "敏感词_zzz_dup";
        postJson("/api/admin/sensitive-words", tokenAdmin, "{\"word\":\"" + word + "\"}");
        assertEquals(400, body(postJson("/api/admin/sensitive-words", tokenAdmin,
                "{\"word\":\"" + word + "\"}")).get("code").asInt());
        // 清理
        JsonNode words = body(getReq("/api/admin/sensitive-words", tokenAdmin)).get("data");
        for (JsonNode w : words) {
            if (word.equals(w.get("word").asText())) {
                deleteReq("/api/admin/sensitive-words/" + w.get("id").asLong(), tokenAdmin);
            }
        }
    }

    // ===== 管理端：公告/日志/仪表盘 =====

    @Test @Order(60)
    void 公告广播() throws Exception {
        assertEquals(200, body(postJson("/api/admin/announcements", tokenAdmin,
                "{\"content\":\"系统测试公告\"}")).get("code").asInt());
        JsonNode notifs = body(getReq("/api/notifications?page=1&size=50", tokenU)).get("data").get("list");
        boolean found = false;
        for (JsonNode n : notifs) {
            if ("announcement".equals(n.get("type").asText())) found = true;
        }
        assertTrue(found, "普通用户应收到公告通知");
    }

    @Test @Order(61)
    void 操作日志记录() throws Exception {
        JsonNode node = body(getReq("/api/admin/logs?page=1&size=50", tokenAdmin));
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("total").asLong() >= 1, "管理操作应有日志");
    }

    @Test @Order(62)
    void 仪表盘统计() throws Exception {
        JsonNode node = body(getReq("/api/admin/dashboard", tokenAdmin));
        assertEquals(200, node.get("code").asInt());
        assertTrue(node.get("data").get("userCount").asLong() >= 1);
        assertTrue(node.get("data").get("momentCount").asLong() >= 1);
        assertTrue(node.get("data").get("pendingReports").asLong() >= 0);
    }
}
