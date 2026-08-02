package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态模块测试：发布/时间线/删除/点赞/赞列表 + 边界 + 敏感词 + 管理端置顶隐藏对排序的影响。
 */
class MomentTest extends TestBase {

    private static final String PREFIX = "t_mom_";
    private static String tokenA, tokenB, tokenAdmin;
    private static Long momentId;
    private static final String SENSITIVE = "敏感词_zzz_moment";

    @Test @Order(1)
    void 准备用户() throws Exception {
        tokenA = registerOrLogin(PREFIX + "a", "pass123", "动态用户A");
        tokenB = registerOrLogin(PREFIX + "b", "pass123", "动态用户B");
        tokenAdmin = adminToken();
        assertNotNull(tokenA);
        assertNotNull(tokenB);
        assertNotNull(tokenAdmin);
    }

    @Test @Order(2)
    void 准备敏感词() throws Exception {
        // 幂等：已存在也接受
        postJson("/api/admin/sensitive-words", tokenAdmin, "{\"word\":\"" + SENSITIVE + "\"}");
    }

    // ===== 发布 =====

    @Test @Order(10)
    void 发动态成功() throws Exception {
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"边界测试动态内容\"}");
        JsonNode node = body(r);
        assertEquals(200, node.get("code").asInt());
        assertEquals("动态用户A", node.get("data").get("nickname").asText(), "昵称应为发布者快照");
        momentId = node.get("data").get("id").asLong();
        assertNotNull(momentId);
    }

    @Test @Order(11)
    void 空内容被拒() throws Exception {
        assertEquals(400, body(postJson("/api/moments", tokenA, "{\"content\":\"\"}")).get("code").asInt());
    }

    @Test @Order(12)
    void 缺失内容被拒() throws Exception {
        assertEquals(400, body(postJson("/api/moments", tokenA, "{}")).get("code").asInt());
    }

    @Test @Order(13)
    void 内容超2000字被拒() throws Exception {
        String longContent = "长".repeat(2001);
        assertEquals(400, body(postJson("/api/moments", tokenA,
                "{\"content\":\"" + longContent + "\"}")).get("code").asInt());
    }

    @Test @Order(14)
    void 恰好2000字允许() throws Exception {
        String content = "恰".repeat(2000);
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"" + content + "\"}");
        assertEquals(200, body(r).get("code").asInt());
        long id = body(r).get("data").get("id").asLong();
        assertEquals(200, body(deleteReq("/api/moments/" + id, tokenA)).get("code").asInt(), "清理测试数据");
    }

    @Test @Order(15)
    void 含敏感词被拒() throws Exception {
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"这里包含" + SENSITIVE + "啊\"}");
        JsonNode node = body(r);
        assertEquals(400, node.get("code").asInt());
        assertTrue(node.get("msg").asText().contains(SENSITIVE), "错误信息应指明命中词");
    }

    // ===== 时间线 =====

    @Test @Order(20)
    void 时间线含刚发布的动态() throws Exception {
        JsonNode node = body(getReq("/api/moments?page=1&size=20", null));
        assertEquals(200, node.get("code").asInt());
        boolean found = false;
        for (JsonNode m : node.get("data").get("list")) {
            if (m.get("id").asLong() == momentId) found = true;
        }
        assertTrue(found, "时间线应包含刚发布的动态");
    }

    @Test @Order(21)
    void 时间线liked状态区分() throws Exception {
        // B 尚未点赞：B 看到 liked=false；未登录看到 liked=null
        JsonNode asB = body(getReq("/api/moments?page=1&size=50", tokenB));
        boolean found = false;
        for (JsonNode m : asB.get("data").get("list")) {
            if (m.get("id").asLong() == momentId && !m.get("liked").asBoolean()) found = true;
        }
        assertTrue(found, "未点赞用户应看到 liked=false");
        JsonNode anon = body(getReq("/api/moments?page=1&size=50", null));
        for (JsonNode m : anon.get("data").get("list")) {
            if (m.get("id").asLong() == momentId) {
                assertTrue(m.get("liked").isNull(), "未登录时 liked 应为 null");
            }
        }
    }

    @Test @Order(22)
    void 分页参数生效() throws Exception {
        JsonNode p1 = body(getReq("/api/moments?page=1&size=5", null));
        assertEquals(5, p1.get("data").get("size").asInt());
        assertTrue(p1.get("data").get("total").asLong() >= 1);
    }

    // ===== 点赞 =====

    @Test @Order(30)
    void 点赞返回liked_true() throws Exception {
        JsonNode r = body(postJson("/api/moments/" + momentId + "/like", tokenB, null));
        assertEquals(200, r.get("code").asInt());
        assertTrue(r.get("data").get("liked").asBoolean());
    }

    @Test @Order(31)
    void 取消点赞返回liked_false() throws Exception {
        JsonNode r = body(postJson("/api/moments/" + momentId + "/like", tokenB, null));
        assertFalse(r.get("data").get("liked").asBoolean());
    }

    @Test @Order(32)
    void 再点赞恢复() throws Exception {
        JsonNode r = body(postJson("/api/moments/" + momentId + "/like", tokenB, null));
        assertTrue(r.get("data").get("liked").asBoolean());
    }

    @Test @Order(33)
    void 赞列表包含点赞者() throws Exception {
        JsonNode r = body(getReq("/api/moments/" + momentId + "/likes", null));
        assertEquals(200, r.get("code").asInt());
        assertEquals(1, r.get("data").get("total").asLong(), "当前应只有 B 点赞");
        assertEquals("动态用户B", r.get("data").get("list").get(0).get("nickname").asText());
    }

    @Test @Order(34)
    void 点赞后时间线liked状态为true() throws Exception {
        // B 当前已点赞，B 看时间线应为 liked=true
        JsonNode asB = body(getReq("/api/moments?page=1&size=50", tokenB));
        boolean found = false;
        for (JsonNode m : asB.get("data").get("list")) {
            if (m.get("id").asLong() == momentId && m.get("liked").asBoolean()) found = true;
        }
        assertTrue(found, "点赞者应看到 liked=true");
    }

    @Test @Order(35)
    void 点赞不存在的动态() throws Exception {
        // P0-1/P1-6 修复后：明确返回 400，不再返回模糊的 liked=false
        JsonNode r = body(postJson("/api/moments/999999/like", tokenB, null));
        assertEquals(400, r.get("code").asInt(), "点赞不存在的动态应明确返回 400");
    }

    @Test @Order(36)
    void 动态likeCount与赞列表一致() throws Exception {
        JsonNode r = body(getReq("/api/moments?page=1&size=50", null));
        for (JsonNode m : r.get("data").get("list")) {
            if (m.get("id").asLong() == momentId) {
                assertEquals(1, m.get("likeCount").asInt(), "likeCount 应与赞列表一致");
            }
        }
    }

    // ===== 删除归属 =====

    @Test @Order(40)
    void 不能删除别人的动态() throws Exception {
        assertEquals(400, body(deleteReq("/api/moments/" + momentId, tokenB)).get("code").asInt());
    }

    @Test @Order(41)
    void 删除不存在的动态() throws Exception {
        assertEquals(400, body(deleteReq("/api/moments/999999", tokenA)).get("code").asInt());
    }

    // ===== 管理端对排序的影响 =====

    @Test @Order(50)
    void 管理员置顶后时间线第一条是置顶动态() throws Exception {
        assertEquals(200, body(putJson("/api/admin/moments/" + momentId + "/pin", tokenAdmin, null)).get("code").asInt());
        JsonNode list = body(getReq("/api/moments?page=1&size=5", null)).get("data").get("list");
        assertEquals(momentId, list.get(0).get("id").asLong(), "置顶动态应排第一");
    }

    @Test @Order(51)
    void 管理员取消置顶() throws Exception {
        assertEquals(200, body(putJson("/api/admin/moments/" + momentId + "/unpin", tokenAdmin, null)).get("code").asInt());
    }

    @Test @Order(52)
    void 管理员隐藏后时间线不可见() throws Exception {
        assertEquals(200, body(putJson("/api/admin/moments/" + momentId + "/hide", tokenAdmin, null)).get("code").asInt());
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            assertNotEquals(momentId, m.get("id").asLong(), "被隐藏的动态不应出现在时间线");
        }
    }

    @Test @Order(53)
    void 管理员恢复后时间线可见() throws Exception {
        assertEquals(200, body(putJson("/api/admin/moments/" + momentId + "/restore", tokenAdmin, null)).get("code").asInt());
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        boolean found = false;
        for (JsonNode m : list) {
            if (m.get("id").asLong() == momentId) found = true;
        }
        assertTrue(found, "恢复后动态应重新可见");
    }

    @Test @Order(54)
    void 作者删除动态后时间线不可见() throws Exception {
        assertEquals(200, body(deleteReq("/api/moments/" + momentId, tokenA)).get("code").asInt());
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            assertNotEquals(momentId, m.get("id").asLong(), "删除后的动态不应出现在时间线");
        }
    }

    // ===== 我的动态 =====

    @Test @Order(60)
    void 我的动态包含被隐藏的() throws Exception {
        // A 再发一条并删除自己看？此处验证 listMine 语义：发两条，隐藏后仍可见
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"我的动态测试\"}");
        long id = body(r).get("data").get("id").asLong();
        putJson("/api/admin/moments/" + id + "/hide", tokenAdmin, null);
        JsonNode mine = body(getReq("/api/users/me/moments?page=1&size=20", tokenA));
        boolean found = false;
        for (JsonNode m : mine.get("data").get("list")) {
            if (m.get("id").asLong() == id) found = true;
        }
        assertTrue(found, "作者应能在我的动态中看到被隐藏的动态");
        // 清理
        deleteReq("/api/moments/" + id, tokenA);
    }
}
