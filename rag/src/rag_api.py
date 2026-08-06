"""
RAG 服务接口（融入 japy-framework 的独立 API，非流式 JSON）：
- POST /api/rag/sync       同步索引（{novel_id} 或 {} 全量）
- POST /api/rag/ask        问答（{novel_id, question} → {answer, sources, meta}）
- GET  /api/rag/status     索引状态
- GET  /api/rag/health     Java 探活
独立于旧 web_app.py（保留给 rag 自带页面）。
"""
import logging
import threading

from fastapi import FastAPI, HTTPException

import pg_store
from agent import build_context, generate_answer
from retriever_pg import HybridRetriever
from sync_service import sync_novel, sync_all

logger = logging.getLogger("rag.api")
app = FastAPI(title="Japy RAG Service", version="1.0")

# 检索器缓存：novel_id → HybridRetriever（BM25 索引重构建昂贵，缓存复用）
_retrievers = {}
_retriever_lock = threading.Lock()
# 同步锁：同书互斥
_sync_locks = {}


def _get_retriever(novel_id: int) -> HybridRetriever:
    with _retriever_lock:
        if novel_id not in _retrievers:
            _retrievers[novel_id] = HybridRetriever(novel_id)
        return _retrievers[novel_id]


def _invalidate(novel_id: int):
    with _retriever_lock:
        _retrievers.pop(novel_id, None)


@app.get("/api/rag/health")
def health():
    return {"ok": True, "service": "japy-rag"}


@app.post("/api/rag/sync")
def sync(body: dict = None):
    body = body or {}
    novel_id = body.get("novel_id")
    if novel_id:
        lock = _sync_locks.setdefault(novel_id, threading.Lock())
        with lock:
            try:
                result = sync_novel(int(novel_id))
            except Exception as e:
                logger.exception("sync failed")
                raise HTTPException(status_code=500, detail=f"同步失败: {e}")
        _invalidate(int(novel_id))
        return {"code": 200, "data": result}
    # 全量
    result = sync_all()
    for n in result["synced"]:
        _invalidate(n.get("novel_id"))
    return {"code": 200, "data": result}


@app.post("/api/rag/ask")
def ask(body: dict):
    novel_id = body.get("novel_id")
    question = (body.get("question") or "").strip()
    if not novel_id or not question:
        raise HTTPException(status_code=400, detail="novel_id 与 question 必填")
    if not pg_store.has_index(int(novel_id)):
        raise HTTPException(status_code=409,
                            detail="该书索引尚未构建，请先在管理端同步")

    retriever = _get_retriever(int(novel_id))
    results, meta = retriever.search(question)

    if not results:
        return {"code": 200, "data": {
            "answer": "抱歉，没有检索到与问题相关的片段。",
            "sources": [], "meta": meta}}

    # 生成（复用 agent.generate_answer，取完整输出）
    # 适配：agent 期望旧字段（chapter_index/chapter_title/chunk_id），新结果用 chapter_no
    agent_chunks = [{
        "chunk_id": r.get("id"),
        "chapter_index": r.get("chapter_no", 0) - 1,
        "chapter_title": f"第{r.get('chapter_no')}章",
        "content": r.get("content", ""),
    } for r in results]
    tokens = list(generate_answer(question, agent_chunks, history=None))
    # 过滤 __SOURCES__ 标记行
    answer_parts = [t for t in tokens if not t.startswith("__SOURCES__")]
    answer = "".join(answer_parts).strip()

    sources = [{
        "chunk_id": r.get("id"),
        "chapter_no": r.get("chapter_no"),
        "content_preview": (r.get("content") or "")[:120],
        "score": round(r.get("score", 0), 4),
    } for r in results]

    return {"code": 200, "data": {
        "answer": answer, "sources": sources, "meta": meta}}


@app.get("/api/rag/status")
def status(novel_id: int = None):
    return {"code": 200, "data": pg_store.chunk_count(novel_id)}
