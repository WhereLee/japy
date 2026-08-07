package com.japy.module.rag.event;

import com.japy.module.rag.RagClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RAG 同步监听：小说上传【事务提交后】异步触发索引同步。
 * 用 AFTER_COMMIT：避免上传事务回滚时仍同步了不存在的小说。
 * 失败不阻塞上传（记录日志，管理端可手动重试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagSyncListener {

    private final RagClient ragClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNovelUploaded(NovelUploadedEvent event) {
        try {
            var result = ragClient.sync(event.getNovelId());
            log.info("RAG 同步完成 novelId={}: {}", event.getNovelId(), result);
        } catch (Exception e) {
            log.warn("RAG 同步失败 novelId={}（可管理端手动重试）: {}",
                    event.getNovelId(), e.getMessage());
        }
    }
}
