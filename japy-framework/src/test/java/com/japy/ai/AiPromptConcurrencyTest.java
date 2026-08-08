package com.japy.ai;

import com.japy.base.AbstractIntegrationTest;
import com.japy.module.ai.service.AiPromptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 并发编辑契约：同 code 并发 update 串行化（行锁），版本号不撞唯一约束、无死锁 */
class AiPromptConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private AiPromptService promptService;

    @Test
    void concurrentUpdatesAreSerialized() throws Exception {
        String code = "novel_qa";
        String original = promptService.getContent(code);
        int maxV = promptService.versions(code).get(0).getVersion();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer>[] futures = new Future[4];
        for (int i = 0; i < 4; i++) {
            final int n = i;
            futures[i] = pool.submit(() -> {
                ready.countDown();
                go.await();
                return promptService.update(code, original + "\n【并发" + n + "】", null).getVersion();
            });
        }
        ready.await();
        go.countDown();
        int[] versions = new int[4];
        for (int i = 0; i < 4; i++) {
            versions[i] = futures[i].get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 四个版本必须连续递增（串行化），且无重复
        java.util.Arrays.sort(versions);
        assertArrayEquals(new int[]{maxV + 1, maxV + 2, maxV + 3, maxV + 4}, versions,
                "并发编辑必须串行化分配连续版本号");
        // 最终只有一行生效
        assertEquals(1, promptService.versions(code).stream().filter(v -> v.getStatus() == 1).count());
    }
}
