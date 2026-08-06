package com.japy.module.rag.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 小说上传完成事件：触发 RAG 索引同步（异步，不阻塞上传响应） */
@Getter
@RequiredArgsConstructor
public class NovelUploadedEvent {
    private final Long novelId;
}
