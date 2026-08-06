package com.japy.module.rag.event;

import com.japy.module.rag.RagClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * RAG 同步监听：小说上传后异步触发索引同步。
 * 失败不阻塞上传（记录日志，管理端可手动重试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagSyncListener {

    private final RagClient ragClient;

    @Async
    @EventListener
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
