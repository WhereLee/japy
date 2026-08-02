package com.japy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发测试：
 * 1. 同一用户并发点赞同一动态 → 唯一约束下不得出现 5xx，最终计数与行数一致
 * 2. 不同用户并发点赞同一动态 → 全部成功，计数正确
 */
class ConcurrencyTest extends TestBase {

    private static final String PREFIX = "t_conc_";

    @Test
    void 同一用户并发点赞不报错且计数一致() throws Exception {
        String token = registerOrLogin(PREFIX + "same", "pass123", "并发同户");
        MvcResult r = postJson("/api/moments", token, "{\"content\":\"并发点赞动态\"}");
        assertEquals(200, body(r).get("code").asInt());
        long mid = body(r).get("data").get("id").asLong();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                MvcResult resp = postJson("/api/moments/" + mid + "/like", token, null);
                return body(resp).get("code").asInt();
            }));
        }
        start.countDown();
        int okCount = 0;
        for (Future<Integer> f : futures) {
            if (f.get() == 200) okCount++;
        }
        pool.shutdown();

        assertEquals(threads, okCount, "同一用户并发点赞不应出现 5xx（唯一约束需幂等处理）");

        // 最终状态自洽：likeCount 与赞列表行数一致，且 ∈ {0,1}
        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        int likeCount = -1;
        for (JsonNode m : list) {
            if (m.get("id").asLong() == mid) likeCount = m.get("likeCount").asInt();
        }
        assertTrue(likeCount == 0 || likeCount == 1, "同一用户并发点赞后 likeCount 应为0或1，实际=" + likeCount);

        JsonNode likes = body(getReq("/api/moments/" + mid + "/likes", null)).get("data");
        long rowCount = likes.get("total").asLong();
        assertEquals(likeCount, rowCount, "likeCount 必须与赞列表行数一致");

        // 清理
        deleteReq("/api/moments/" + mid, token);
    }

    @Test
    void 不同用户并发点赞全部成功且计数正确() throws Exception {
        String owner = registerOrLogin(PREFIX + "owner", "pass123", "并发作者");
        MvcResult r = postJson("/api/moments", owner, "{\"content\":\"多人点赞动态\"}");
        long mid = body(r).get("data").get("id").asLong();

        int n = 5;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tokens.add(registerOrLogin(PREFIX + "user" + i, "pass123", "并发用户" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (String t : tokens) {
            futures.add(pool.submit(() -> {
                start.await();
                return body(postJson("/api/moments/" + mid + "/like", t, null)).get("code").asInt();
            }));
        }
        start.countDown();
        int okCount = 0;
        for (Future<Integer> f : futures) {
            if (f.get() == 200) okCount++;
        }
        pool.shutdown();
        assertEquals(n, okCount, "不同用户并发点赞应全部成功");

        JsonNode likes = body(getReq("/api/moments/" + mid + "/likes", null)).get("data");
        assertEquals(n, likes.get("total").asLong(), "赞列表应包含全部 n 个用户");

        JsonNode list = body(getReq("/api/moments?page=1&size=50", null)).get("data").get("list");
        for (JsonNode m : list) {
            if (m.get("id").asLong() == mid) {
                assertEquals(n, m.get("likeCount").asInt(), "likeCount 应等于 n");
            }
        }
        // 清理
        deleteReq("/api/moments/" + mid, owner);
    }
}
