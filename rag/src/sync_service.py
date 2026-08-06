"""
RAG 同步服务：japy-framework PG 自然段 → 切块 → 编码 → 写 jf_rag_chunk
流程：load_paragraphs(novel_id) → chunker_paragraph → embedder.encode → pg_store.save_chunks
"""
import logging
import time
from typing import Dict, List

import pg_store
from chunker_paragraph import chunk_paragraphs
from embedder import Embedder

logger = logging.getLogger("rag.sync")


def sync_novel(novel_id: int, strategy: str = "paragraph") -> Dict:
    """同步一本书：重建检索块 + 向量化。返回统计"""
    t0 = time.perf_counter()

    # 1. 拉自然段（事实源）
    paragraphs = pg_store.load_paragraphs(novel_id)
    if not paragraphs:
        return {"novel_id": novel_id, "error": "无自然段数据"}

    # 2. 切块（paragraph 策略：≤500 整段 / >500 二分+引号保护）
    chunks = chunk_paragraphs(paragraphs, novel_id, strategy)
    logger.info(f"novel {novel_id}: {len(paragraphs)} 段 → {len(chunks)} 块")

    # 3. 编码（批处理，768 维）
    embedder = Embedder(batch_size=48)
    texts = [c["content"] for c in chunks]
    embeddings = embedder.encode_batch(texts)

    # 4. 清旧 + 写新
    pg_store.clear_chunks(novel_id)
    pg_store.save_chunks(chunks, embeddings)

    return {
        "novel_id": novel_id,
        "paragraphs": len(paragraphs),
        "chunks": len(chunks),
        "strategy": strategy,
        "elapsed": round(time.perf_counter() - t0, 2),
    }


def sync_all() -> Dict:
    """同步全部已上架小说"""
    novels = pg_store.load_novels()
    results = []
    for n in novels:
        try:
            results.append(sync_novel(n["id"]))
        except Exception as e:
            results.append({"novel_id": n["id"], "error": str(e)})
    return {"synced": results}
