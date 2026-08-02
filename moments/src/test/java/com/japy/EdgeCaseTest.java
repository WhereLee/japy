package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 边缘情况回归测试：覆盖调研发现的 P0/P1 问题修复。
 * P0-1 孤儿数据 · P0-2 计数一致 · P0-3 size上限 · P1-5 通知节流 · P1-6 点赞语义 · P1-7 子回复分页 · P1-8 游标分页
 */
class EdgeCaseTest extends TestBase {

    private static final String PREFIX = "t_edge_";
    private static String tokenA, tokenB, tokenAdmin;
    private static Long momentId;

    @Test @Order(1)
    void 准备数据() throws Exception {
        tokenA = registerOrLogin(PREFIX + "a", "pass123", "边缘用户A");
        tokenB = registerOrLogin(PREFIX + "b", "pass123", "边缘用户B");
        tokenAdmin = adminToken();
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"边缘测试动态\"}");
        momentId = body(r).get("data").get("id").asLong();
        assertNotNull(momentId);
    }

    // ===== P0-1 孤儿数据 =====

    @Test @Order(10)
    void 删除动态后评论不可查() throws Exception {
        // 造一条评论
        postJson("/api/comments", tokenB, "{\"momentId\":" + momentId + ",\"content\":\"删除前评论\"}");
        // 删除动态
        assertEquals(200, body(deleteReq("/api/moments/" + momentId, tokenA)).get("code").asInt());
        // 评论列表明确返回 400
        JsonNode r = body(getReq("/api/comments?momentId=" + momentId + "&page=1&size=20", null));
        assertEquals(400, r.get("code").asInt(), "删除动态后评论列表应返回400");
    }

    @Test @Order(11)
    void 删除动态后赞列表不可查() throws Exception {
        // 新造一条动态，点赞后删除
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"删除前点赞\"}");
        long mid = body(r).get("data").get("id").asLong();
        postJson("/api/moments/" + mid + "/like", tokenB, null);
        assertEquals(200, body(deleteReq("/api/moments/" + mid, tokenA)).get("code").asInt());
        JsonNode likes = body(getReq("/api/moments/" + mid + "/likes", null));
        assertEquals(400, likes.get("code").asInt(), "删除动态后赞列表应返回400");
    }

    // ===== P0-2 计数一致 =====

    @Test @Order(20)
    void 隐藏评论时计数递减() throws Exception {
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"计数测试动态\"}");
        long mid = body(r).get("data").get("id").asLong();
        MvcResult c = postJson("/api/comments", tokenB, "{\"momentId\":" + mid + ",\"content\":\"第一条\"}");
        long cid = body(c).get("data").get("id").asLong();
        postJson("/api/comments", tokenB, "{\"momentId\":" + mid + ",\"content\":\"第二条\"}");

        // 2 条评论
        assertEquals(2, commentCountOf(mid));

        // 隐藏一条 → 计数变 1
        assertEquals(200, body(putJson("/api/admin/comments/" + cid + "/hide", tokenAdmin, null)).get("code").asInt());
        assertEquals(1, commentCountOf(mid), "隐藏评论后计数应递减");

        // 恢复 → 计数变 2
        assertEquals(200, body(putJson("/api/admin/comments/" + cid + "/restore", tokenAdmin, null)).get("code").asInt());
        assertEquals(2, commentCountOf(mid), "恢复评论后计数应递增");

        // 删除（可见状态）→ 计数变 1
        assertEquals(200, body(deleteReq("/api/admin/comments/" + cid, tokenAdmin)).get("code").asInt());
        assertEquals(1, commentCountOf(mid), "删除可见评论后计数应递减");

        // 清理
        deleteReq("/api/moments/" + mid, tokenA);
    }

    @Test @Order(21)
    void 评论列表与计数一致() throws Exception {
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"计数一致性动态\"}");
        long mid = body(r).get("data").get("id").asLong();
        postJson("/api/comments", tokenB, "{\"momentId\":" + mid + ",\"content\":\"评论A\"}");
        postJson("/api/comments", tokenB, "{\"momentId\":" + mid + ",\"content\":\"评论B\"}");
        JsonNode list = body(getReq("/api/comments?momentId=" + mid + "&page=1&size=20", null)).get("data");
        assertEquals(2, list.get("total").asLong());
        assertEquals(2, commentCountOf(mid), "列表可见数与计数应一致");
        deleteReq("/api/moments/" + mid, tokenA);
    }

    // ===== P0-3 size 上限 =====

    @Test @Order(30)
    void 分页size超限被规整() throws Exception {
        JsonNode r = body(getReq("/api/moments?page=1&size=10000", null)).get("data");
        assertEquals(100, r.get("size").asInt(), "size 超限应被规整到 100");
        // 评论列表同样受限制
        MvcResult m = postJson("/api/moments", tokenA, "{\"content\":\"size测试动态\"}");
        long mid = body(m).get("data").get("id").asLong();
        JsonNode c = body(getReq("/api/comments?momentId=" + mid + "&page=1&size=9999", null));
        assertEquals(200, c.get("code").asInt());
        assertEquals(100, c.get("data").get("size").asInt(), "评论列表 size 超限同样应规整");
        deleteReq("/api/moments/" + mid, tokenA);
    }

    // ===== P1-5 通知节流 =====

    @Test @Order(40)
    void 反复点赞只产生一条通知() throws Exception {
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"通知节流动态\"}");
        long mid = body(r).get("data").get("id").asLong();
        // B 点赞-取消-点赞-取消-点赞（3次成功点赞）
        for (int i = 0; i < 3; i++) {
            postJson("/api/moments/" + mid + "/like", tokenB, null);
            postJson("/api/moments/" + mid + "/like", tokenB, null);
            postJson("/api/moments/" + mid + "/like", tokenB, null);
        }
        // A 的未读 like 通知只应有 1 条（针对该动态）
        JsonNode list = body(getReq("/api/notifications?page=1&size=50", tokenA)).get("data").get("list");
        int likeCount = 0;
        for (JsonNode n : list) {
            if ("like".equals(n.get("type").asText()) && n.get("refId").asLong() == mid && n.get("isRead").asInt() == 0) {
                likeCount++;
            }
        }
        assertEquals(1, likeCount, "反复点赞只应产生一条未读通知（节流）");
        deleteReq("/api/moments/" + mid, tokenA);
    }

    // ===== P1-6 点赞语义 =====

    @Test @Order(50)
    void 点赞被隐藏的动态返回400() throws Exception {
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"将被隐藏点赞\"}");
        long mid = body(r).get("data").get("id").asLong();
        assertEquals(200, body(putJson("/api/admin/moments/" + mid + "/hide", tokenAdmin, null)).get("code").asInt());
        assertEquals(400, body(postJson("/api/moments/" + mid + "/like", tokenB, null)).get("code").asInt(),
                "点赞被隐藏的动态应返回400");
        deleteReq("/api/moments/" + mid, tokenA);
    }

    // ===== P1-7 子回复分页 =====

    @Test @Order(60)
    void 子回复超过replySize只返回最新部分() throws Exception {
        MvcResult r = postJson("/api/moments", tokenA, "{\"content\":\"子回复分页动态\"}");
        long mid = body(r).get("data").get("id").asLong();
        MvcResult c = postJson("/api/comments", tokenB, "{\"momentId\":" + mid + ",\"content\":\"顶层评论\"}");
        long topId = body(c).get("data").get("id").asLong();
        // 造 25 条子回复（同一用户可多次回复同一顶层评论）
        for (int i = 0; i < 25; i++) {
            postJson("/api/comments", tokenA,
                    "{\"momentId\":" + mid + ",\"parentId\":" + topId + ",\"content\":\"回复" + i + "\"}");
        }
        // replySize=10 → 只返回最新 10 条
        JsonNode list = body(getReq("/api/comments?momentId=" + mid + "&page=1&size=20&replySize=10", null))
                .get("data").get("list");
        assertEquals(1, list.size());
        assertEquals(10, list.get(0).get("replies").size(), "replySize=10 应只返回最新10条");
        // 最新 10 条 = id 最大的 10 条
        List<Long> replyIds = new ArrayList<>();
        for (JsonNode rep : list.get(0).get("replies")) {
            replyIds.add(rep.get("id").asLong());
        }
        // 取全部子回复 id 排序后与返回的比对：返回的应是最大的10个
        JsonNode all = body(getReq("/api/comments?momentId=" + mid + "&page=1&size=20&replySize=100", null))
                .get("data").get("list");
        List<Long> allIds = new ArrayList<>();
        for (JsonNode rep : all.get(0).get("replies")) {
            allIds.add(rep.get("id").asLong());
        }
        allIds.sort(Long::compareTo);
        List<Long> latest10 = allIds.subList(allIds.size() - 10, allIds.size());
        Set<Long> got = new HashSet<>(replyIds);
        Set<Long> expect = new HashSet<>(latest10);
        assertEquals(expect, got, "返回的应是 id 最大的10条子回复");
        deleteReq("/api/moments/" + mid, tokenA);
    }

    // ===== P1-8 游标分页 =====

    @Test @Order(70)
    void 游标分页无重复无遗漏() throws Exception {
        String token = registerOrLogin(PREFIX + "cursor", "pass123", "游标用户");
        // 发 6 条动态
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MvcResult r = postJson("/api/moments", token, "{\"content\":\"游标动态" + i + "\"}");
            ids.add(body(r).get("data").get("id").asLong());
        }
        // 用 size=2 游标翻页收集
        List<Long> collected = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        while (guard++ < 10) {
            String url = "/api/moments?page=1&size=2" + (cursor != null ? "&cursor=" + cursor : "");
            JsonNode page = body(getReq(url, null)).get("data");
            List<JsonNode> list = new ArrayList<>();
            page.get("list").forEach(list::add);
            if (list.isEmpty()) break;
            for (JsonNode m : list) {
                collected.add(m.get("id").asLong());
            }
            // 取本页最后一条构造游标
            JsonNode last = list.get(list.size() - 1);
            cursor = last.get("createdAt").asText() + "_" + last.get("id").asLong();
        }
        // 无重复
        Set<Long> unique = new HashSet<>(collected);
        assertEquals(collected.size(), unique.size(), "游标翻页不应有重复");
        // 6 条全部被收集
        assertTrue(unique.containsAll(ids), "6 条动态应全部被收集到");
        // 清理
        for (Long id : ids) {
            deleteReq("/api/moments/" + id, token);
        }
    }

    @Test @Order(71)
    void 非法cursor返回400() throws Exception {
        JsonNode r = body(getReq("/api/moments?page=1&size=2&cursor=bad-cursor-format", null));
        assertEquals(400, r.get("code").asInt(), "非法 cursor 应返回 400");
    }

    // ===== 辅助 =====

    private int commentCountOf(long momentId) throws Exception {
        JsonNode list = body(getReq("/api/moments?page=1&size=100", null)).get("data").get("list");
        for (JsonNode m : list) {
            if (m.get("id").asLong() == momentId) {
                return m.get("commentCount").asInt();
            }
        }
        return -1;
    }
}
