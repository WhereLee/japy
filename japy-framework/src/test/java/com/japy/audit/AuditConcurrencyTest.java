package com.japy.audit;

import com.japy.base.AbstractIntegrationTest;
import com.japy.common.BusinessException;
import com.japy.module.audit.entity.NovelAudit;
import com.japy.module.audit.mapper.NovelAuditMapper;
import com.japy.module.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** 审核幂等契约：两个管理员并发处理同一条审核，只有一人成功（防重复处理） */
class AuditConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private AuditService auditService;
    @Autowired
    private NovelAuditMapper auditMapper;

    @Test
    void concurrentHandleOnlyOneSucceeds() throws Exception {
        // 造一条 PENDING 审核
        NovelAudit audit = new NovelAudit();
        audit.setNovelId(99999L);
        audit.setAuditType("UPLOAD");
        audit.setResult("PENDING");
        audit.setRuleHits("[]");
        auditMapper.insert(audit);
        Long id = audit.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Boolean>[] futures = new Future[2];
        for (int i = 0; i < 2; i++) {
            final long adminId = 100 + i;
            futures[i] = pool.submit(() -> {
                go.await();
                try {
                    auditService.handle(id, "PASS", adminId, "并发处理");
                    return true;   // 成功
                } catch (BusinessException e) {
                    return false;  // 已被他人处理
                }
            });
        }
        go.countDown();
        boolean r1 = futures[0].get();
        boolean r2 = futures[1].get();
        pool.shutdown();

        // 恰好一人成功
        assertNotEquals(r1, r2, "并发处理同一条审核必须恰好一人成功");
        // 最终只有一次处理结果
        NovelAudit after = auditMapper.selectById(id);
        assertEquals("PASS", after.getResult());
        assertTrue(after.getAuditorId() == 100L || after.getAuditorId() == 101L);
    }
}
