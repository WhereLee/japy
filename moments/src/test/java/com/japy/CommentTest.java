package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评论模块测试：顶层评论/楼中楼回复/非法父评论/计数一致性/删除级联。
 */
class CommentTest extends TestBase {

    private static final String PREFIX = "t_cmt_";
    private static String tokenA, tokenB;
    private static Long momentId, topId, replyId;

    @Test @Order(1)
    void 准备数据() throws Exception {
        tokenA = registerOrLogin(PREFIX + "a", "pass123", "评论用户A");
        tokenB = registerOrLogin(PREFIX + "b", "pass123", "评论用户B");
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"评论测试动态\"}");
        momentId = body(r).get("data").get("id").asLong();
        assertNotNull(momentId);
    }

    // ===== 顶层评论 =====

    @Test @Order(10)
    void 顶层评论成功() throws Exception {
        MvcResult r = postJson("/api/comments", tokenB,
                "{\"momentId\":" + momentId + ",\"content\":\"第一条评论\"}");
        JsonNode node = body(r);
        assertEquals(200, node.get("code").asInt());
        assertEquals("评论用户B", node.get("data").get("nickname").asText());
        assertTrue(node.get("data").get("parentId").isNull(), "顶层评论 parentId 应为空");
        topId = node.get("data").get("id").asLong();
    }

    @Test @Order(11)
    void 空评论被拒() throws Exception {
        assertEquals(400, body(postJson("/api/comments", tokenB,
                "{\"momentId\":" + momentId + ",\"content\":\"\"}")).get("code").asInt());
    }

    @Test @Order(12)
    void 缺失momentId被拒() throws Exception {
        assertEquals(400, body(postJson("/api/comments", tokenB,
                "{\"content\":\"没有momentId\"}")).get("code").asInt());
    }

    @Test @Order(13)
    void 评论超500字被拒() throws Exception {
        String longComment = "评".repeat(501);
        assertEquals(400, body(postJson("/api/comments", tokenB,
                "{\"momentId\":" + momentId + ",\"content\":\"" + longComment + "\"}")).get("code").asInt());
    }

    @Test @Order(14)
    void 评论不存在的动态被拒() throws Exception {
        assertEquals(400, body(postJson("/api/comments", tokenB,
                "{\"momentId\":999999,\"content\":\"评论不存在的动态\"}")).get("code").asInt());
    }

    // ===== 楼中楼回复 =====

    @Test @Order(20)
    void 回复顶层评论成功() throws Exception {
        MvcResult r = postJson("/api/comments", tokenA,
                "{\"momentId\":" + momentId + ",\"parentId\":" + topId + ",\"replyTo\":\"评论用户B\",\"content\":\"回复B\"}");
        JsonNode node = body(r);
        assertEquals(200, node.get("code").asInt());
        assertEquals(topId, node.get("data").get("parentId").asLong(), "回复的 parentId 应指向顶层评论");
        replyId = node.get("data").get("id").asLong();
    }

    @Test @Order(21)
    void 回复不存在的评论被拒() throws Exception {
        assertEquals(400, body(postJson("/api/comments", tokenA,
                "{\"momentId\":" + momentId + ",\"parentId\":999999,\"content\":\"回复不存在的评论\"}")).get("code").asInt());
    }

    @Test @Order(22)
    void 回复其他动态的评论被拒() throws Exception {
        // 造另一条动态和其评论
        MvcResult r = postJson("/api/moments", tokenB, "{\"content\":\"另一条动态\"}");
        long otherMoment = body(r).get("data").get("id").asLong();
        MvcResult rc = postJson("/api/comments", tokenB,
                "{\"momentId\":" + otherMoment + ",\"content\":\"另一条评论\"}");
        long otherComment = body(rc).get("data").get("id").asLong();
        // 用 A 的动态回复 B 动态下的评论 → 不匹配
        assertEquals(400, body(postJson("/api/comments", tokenA,
                "{\"momentId\":" + momentId + ",\"parentId\":" + otherComment + ",\"content\":\"跨动态回复\"}")).get("code").asInt());
        // 清理
        deleteReq("/api/moments/" + otherMoment, tokenB);
    }

    @Test @Order(23)
    void 回复子评论被拒() throws Exception {
        // replyId 是子评论，不能再被回复
        assertEquals(400, body(postJson("/api/comments", tokenB,
                "{\"momentId\":" + momentId + ",\"parentId\":" + replyId + ",\"content\":\"套娃回复\"}")).get("code").asInt());
    }

    // ===== 列表结构 =====

    @Test @Order(30)
    void 评论列表返回顶层与子回复() throws Exception {
        JsonNode node = body(getReq("/api/comments?momentId=" + momentId + "&page=1&size=20", null));
        assertEquals(200, node.get("code").asInt());
        assertEquals(1, node.get("data").get("total").asLong(), "顶层评论只有1条");
        JsonNode top = node.get("data").get("list").get(0);
        assertEquals(topId, top.get("id").asLong());
        assertEquals(1, top.get("replies").size(), "顶层评论应带1条子回复");
        assertEquals(replyId, top.get("replies").get(0).get("id").asLong());
        assertEquals("回复B", top.get("replies").get(0).get("content").asText());
    }

    @Test @Order(31)
    void 动态commentCount与评论数一致() throws Exception {
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            if (m.get("id").asLong() == momentId) {
                assertEquals(2, m.get("commentCount").asInt(), "2条评论（1顶层+1回复）");
            }
        }
    }

    // ===== 通知 =====

    @Test @Order(40)
    void 被回复者收到回复通知() throws Exception {
        // B 的评论被 A 回复 → B 收到 reply 通知
        JsonNode notifs = body(getReq("/api/notifications?page=1&size=20", tokenB)).get("data").get("list");
        boolean found = false;
        for (JsonNode n : notifs) {
            if ("reply".equals(n.get("type").asText())) found = true;
        }
        assertTrue(found, "被回复人应收到 reply 通知");
    }

    @Test @Order(41)
    void 动态作者收到评论通知() throws Exception {
        // A 的动态被 B 评论 → A 收到 comment 通知
        JsonNode notifs = body(getReq("/api/notifications?page=1&size=20", tokenA)).get("data").get("list");
        boolean found = false;
        for (JsonNode n : notifs) {
            if ("comment".equals(n.get("type").asText())) found = true;
        }
        assertTrue(found, "动态作者应收到 comment 通知");
    }

    // ===== 删除归属与级联 =====

    @Test @Order(50)
    void 不能删除别人的评论() throws Exception {
        assertEquals(400, body(deleteReq("/api/comments/" + topId, tokenA)).get("code").asInt(), "A 不能删 B 的评论");
    }

    @Test @Order(51)
    void 删除子回复后计数递减() throws Exception {
        assertEquals(200, body(deleteReq("/api/comments/" + replyId, tokenA)).get("code").asInt());
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            if (m.get("id").asLong() == momentId) {
                assertEquals(1, m.get("commentCount").asInt(), "删掉子回复后计数应为1");
            }
        }
    }

    @Test @Order(52)
    void 删除顶层评论级联删除子回复() throws Exception {
        // 先造一个子回复
        MvcResult r = postJson("/api/comments", tokenA,
                "{\"momentId\":" + momentId + ",\"parentId\":" + topId + ",\"content\":\"待级联删除\"}");
        long child = body(r).get("data").get("id").asLong();
        assertEquals(200, body(deleteReq("/api/comments/" + topId, tokenB)).get("code").asInt(), "B 删自己的顶层评论");
        // 子回复也应消失
        JsonNode node = body(getReq("/api/comments?momentId=" + momentId + "&page=1&size=20", null));
        assertEquals(0, node.get("data").get("total").asLong(), "顶层评论删除后列表应为空");
        assertEquals(0, node.get("data").get("list").isEmpty() ? 0 : node.get("data").get("list").get(0).get("replies").size());
        // 计数归零
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            if (m.get("id").asLong() == momentId) {
                assertEquals(0, m.get("commentCount").asInt(), "全部删除后计数应为0");
            }
        }
        // 清理动态
        deleteReq("/api/moments/" + momentId, tokenA);
    }
}
