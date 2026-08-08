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
import time
from typing import Dict, Optional

from fastapi import FastAPI, HTTPException

import pg_store
from agent import build_context, generate_answer
from retriever_pg import HybridRetriever
from sync_service import sync_novel, sync_all

logger = logging.getLogger("rag.api")
app = FastAPI(title="Japy RAG Service", version="1.0")

# 检索器缓存：novel_id → HybridRetriever（BM25 索引重构建昂贵，缓存复用）
# LRU 上限：模型已全局单例，缓存只存"书的 BM25 语料索引"；书多了淘汰最久未用的
_retrievers = {}
_retriever_lock = threading.Lock()
RETRIEVER_CACHE_MAX = 16


def _get_retriever(novel_id: int) -> HybridRetriever:
    with _retriever_lock:
        if novel_id in _retrievers:
            # LRU：命中即移到末尾（dict pop + 重插）
            r = _retrievers.pop(novel_id)
            _retrievers[novel_id] = r
            return r
        retriever = HybridRetriever(novel_id)
        _retrievers[novel_id] = retriever
        # 超限淘汰最久未用（最早插入的）
        while len(_retrievers) > RETRIEVER_CACHE_MAX:
            oldest = next(iter(_retrievers))
            _retrievers.pop(oldest, None)
            logger.info(f"检索器缓存淘汰 novel {oldest}（LRU，上限 {RETRIEVER_CACHE_MAX}）")
        return retriever


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
        novel_id = int(novel_id)
        # 异步执行：立即返回任务已启动，进度经 /api/rag/sync/status 轮询
        start_sync_task(novel_id, all_novels=False)
        return {"code": 200, "data": {"task_started": True, "novel_id": novel_id}}
    # 全量也异步（避免长 HTTP 阻塞）
    start_sync_task(None, all_novels=True)
    return {"code": 200, "data": {"task_started": True, "novel_id": None, "all": True}}


# ---------------------------------------------------------------------------
# 异步同步任务 + 进度状态（切块时间/入库时间/入库进度 信息交互）
# 任务状态存内存 dict（进程重启丢失可接受；生产可迁 Redis/DB）
# ---------------------------------------------------------------------------
_sync_tasks: Dict[int, Dict] = {}       # novel_id(0=全量) → 任务状态
_task_locks: Dict[int, threading.Lock] = {}  # 同书互斥


def _task_state(novel_id: int) -> Dict:
    key = novel_id if novel_id else 0
    if key not in _sync_tasks:
        _sync_tasks[key] = {
            "novel_id": novel_id, "status": "idle",
            "phase": "", "processed": 0, "total": 0,
            "detail": "", "result": None, "error": None,
            "updated_at": time.strftime("%H:%M:%S"),
        }
    return _sync_tasks[key]


def _progress_cb(novel_id: int):
    state = _task_state(novel_id)

    def cb(p: Dict):
        state["phase"] = p["phase"]
        state["processed"] = p["processed"]
        state["total"] = p["total"]
        state["detail"] = p["detail"]
        state["updated_at"] = time.strftime("%H:%M:%S")
    return cb


# 同步任务全局串行：同时最多 1 个同步任务（模型单例 + 防多任务并发加载/推理）
_sync_semaphore = threading.Semaphore(1)


def _run_sync(novel_id: int, all_novels: bool):
    state = _task_state(novel_id)
    # 全局串行：拿不到信号量则排队等待（同步任务不并发，模型只被一份顺序使用）
    if not _sync_semaphore.acquire(timeout=60):
        state["status"] = "failed"
        state["error"] = "等待同步资源超时（已有其他同步任务在跑）"
        return
    try:
        if all_novels:
            state["status"] = "running"
            result = sync_all(progress_cb=_progress_cb(0))
            state["result"] = result
            state["status"] = "done"
            for n in result.get("synced", []):
                _invalidate(n.get("novel_id"))
        else:
            state["status"] = "running"
            result = sync_novel(novel_id, progress_cb=_progress_cb(novel_id))
            if "error" in result:
                state["error"] = result["error"]
                state["status"] = "failed"
            else:
                state["result"] = result
                state["status"] = "done"
        state["updated_at"] = time.strftime("%H:%M:%S")
        if not all_novels:
            _invalidate(novel_id)
    except Exception as e:
        logger.exception("sync task failed")
        state["status"] = "failed"
        state["error"] = str(e)
    finally:
        _sync_semaphore.release()  # 释放全局串行闸门
        # 任务结束 10 分钟后清理状态（防无限增长）
        threading.Timer(600, lambda: _sync_tasks.pop(novel_id if novel_id else 0, None)).start()
        _task_locks.pop(novel_id if novel_id else 0, None)


def start_sync_task(novel_id: Optional[int], all_novels: bool = False):
    """启动异步同步任务（同书互斥；运行中重复触发直接忽略）"""
    key = novel_id if novel_id else 0
    state = _task_state(novel_id)
    if state["status"] == "running":
        return
    lock = _task_locks.setdefault(key, threading.Lock())
    if lock.locked():
        return
    state["status"] = "queued"
    t = threading.Thread(target=_run_sync, args=(novel_id, all_novels), daemon=True)
    t.start()


@app.get("/api/rag/sync/status")
def sync_status(novel_id: int = None):
    """查询同步任务进度：状态/阶段/已处理/总数/分阶段耗时（fetch/chunk/embed/save）"""
    key = novel_id if novel_id else 0
    state = _task_state(novel_id)
    data = {k: state[k] for k in ("novel_id", "status", "phase", "processed", "total", "detail", "updated_at", "error")}
    if state.get("result"):
        r = state["result"]
        data["result"] = r.get("chunks") if isinstance(r, dict) else r
        data["phases"] = (r or {}).get("phases") if isinstance(r, dict) else None
        data["elapsed"] = (r or {}).get("elapsed") if isinstance(r, dict) else None
        data["paragraphs"] = (r or {}).get("paragraphs") if isinstance(r, dict) else None
    return {"code": 200, "data": data}


@app.post("/api/rag/ask")
def ask(body: dict):
    novel_id = body.get("novel_id")
    question = (body.get("question") or "").strip()
    if not novel_id or not question:
        raise HTTPException(status_code=400, detail="novel_id 与 question 必填")
    if len(question) > 500:
        raise HTTPException(status_code=400, detail="问题过长（≤500 字）")
    if not pg_store.has_index(int(novel_id)):
        raise HTTPException(status_code=409,
                            detail="该书索引尚未构建，请先在管理端同步")

    retriever = _get_retriever(int(novel_id))
    results, meta = retriever.search(question)

    if not results:
        return {"code": 200, "data": {
            "answer": "抱歉，没有检索到与问题相关的片段。",
            "sources": [], "meta": meta}}

    # 检索相关性守卫：rerank 得分（logit）< 0 表示检索结果与问题不相关
    # （如问编程/通用知识），此时不调用 LLM（省 token + 防答非所问），
    # 直接给出定位引导。
    if meta.get("top_score", 0) <= 0:
        return {"code": 200, "data": {
            "answer": "我是这本书的问答助手，只讨论与本书相关的内容。请提问本书中的情节、人物或写作手法。",
            "sources": [], "meta": {**meta, "guarded": True}}}

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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
