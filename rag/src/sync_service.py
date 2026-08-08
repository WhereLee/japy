"""
RAG 同步服务：japy-framework PG 自然段 → 切块 → 编码 → 写 jf_rag_chunk
流程：load_paragraphs(novel_id) → chunker_paragraph → embedder.encode → pg_store.save_chunks

sync_novel 支持分阶段计时 + 进度回调（progress_cb 每处理一批调用一次），
供 rag_api 异步任务展示"切块时间/入库时间/入库进度"。
"""
import logging
import time
from typing import Callable, Dict, List, Optional

import pg_store
from chunker_paragraph import chunk_paragraphs
from embedder import get_shared_embedder

logger = logging.getLogger("rag.sync")


def sync_novel(novel_id: int, strategy: str = "paragraph",
               progress_cb: Optional[Callable[[Dict], None]] = None) -> Dict:
    """同步一本书：重建检索块 + 向量化。返回分阶段统计。

    progress_cb(phase, processed, total, detail) 回调用于进度上报：
      phase ∈ fetch/chunk/embed/save；embed 阶段按批推进 processed。
    """
    t0 = time.perf_counter()
    phases = {}

    def _cb(phase: str, processed: int = 0, total: int = 0, detail: str = ""):
        if progress_cb:
            progress_cb({"phase": phase, "processed": processed, "total": total, "detail": detail})

    # 1. 拉自然段（事实源）
    t1 = time.perf_counter()
    paragraphs = pg_store.load_paragraphs(novel_id)
    if not paragraphs:
        return {"novel_id": novel_id, "error": "无自然段数据"}
    phases["fetch_ms"] = round((time.perf_counter() - t1) * 1000, 1)
    _cb("fetch", 0, len(paragraphs), f"拉取 {len(paragraphs)} 个自然段")

    # 2. 切块（paragraph 策略：≤500 整段 / >500 二分+引号保护）
    t1 = time.perf_counter()
    chunks = chunk_paragraphs(paragraphs, novel_id, strategy)
    phases["chunk_ms"] = round((time.perf_counter() - t1) * 1000, 1)
    logger.info(f"novel {novel_id}: {len(paragraphs)} 段 → {len(chunks)} 块 ({phases['chunk_ms']}ms)")
    _cb("chunk", len(chunks), len(chunks), f"切块 {len(paragraphs)} 段 → {len(chunks)} 块")

    # 3. 编码（批处理，768 维），按批推进度
    t1 = time.perf_counter()
    embedder = get_shared_embedder(batch_size=48)  # 全局单例（多书/多任务共享，避免重复加载）
    embeddings = embedder.encode_batch(
        [c["content"] for c in chunks],
        progress_cb=lambda done, total: _cb("embed", done, total, f"向量化 {done}/{total} 块"),
    )
    phases["embed_ms"] = round((time.perf_counter() - t1) * 1000, 1)
    _cb("embed", len(chunks), len(chunks), f"向量化完成 {len(chunks)} 块")

    # 4. 清旧 + 写新
    t1 = time.perf_counter()
    pg_store.clear_chunks(novel_id)
    pg_store.save_chunks(chunks, embeddings)
    phases["save_ms"] = round((time.perf_counter() - t1) * 1000, 1)
    _cb("save", len(chunks), len(chunks), f"入库 {len(chunks)} 块")

    return {
        "novel_id": novel_id,
        "paragraphs": len(paragraphs),
        "chunks": len(chunks),
        "strategy": strategy,
        "phases": phases,
        "elapsed": round(time.perf_counter() - t0, 2),
    }


def sync_all(progress_cb: Optional[Callable[[Dict], None]] = None) -> Dict:
    """同步全部已上架小说"""
    novels = pg_store.load_novels()
    results = []
    for n in novels:
        try:
            results.append(sync_novel(n["id"], progress_cb=progress_cb))
        except Exception as e:
            logger.exception("sync_all novel %s failed", n["id"])
            results.append({"novel_id": n["id"], "error": str(e)})
    return {"synced": results}
