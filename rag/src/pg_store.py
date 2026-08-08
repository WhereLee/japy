"""
PG 向量存储层（替代 ChromaDB）：
- 从 japy-framework 的 PostgreSQL 拉自然段（事实源）
- 切块后写入 jf_rag_chunk（含 pgvector 向量）
- 检索：SQL `ORDER BY embedding <=> 查询向量`（余弦距离，HNSW 索引）
连接配置：仓库根 .env 的 JAPY_DB_URL（12-factor，不入库）
"""
import os
import logging
import threading
from pathlib import Path
from typing import List, Dict, Optional

import numpy as np
import psycopg2
import psycopg2.extras

logger = logging.getLogger("rag.pg_store")

# 仓库根 .env（与 config.py 同一机制）
_REPO_ROOT = Path(__file__).resolve().parent.parent.parent
_ENV_FILE = _REPO_ROOT / ".env"
if _ENV_FILE.exists():
    for line in _ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, _, v = line.partition("=")
            os.environ.setdefault(k.strip(), v.strip())

DEFAULT_DB_URL = "postgresql://postgres:root@localhost:5432/japy_moments"
DB_URL = os.getenv("JAPY_DB_URL", DEFAULT_DB_URL)

# ==================== 数据库连接池（多用户并发核心）====================
# 原实现每请求新建连接：并发峰值 = 请求数 × 3（一次 ask 建 3~4 个连接），无上限。
# 改为 ThreadedConnectionPool（线程安全）+ 信号量限流：
#   - min 5 / max 20：常驻 5 条，突发借到 20（PG 每连接 ~10MB，20 条远低于 max_connections=100）
#   - 信号量 acquire(timeout=5s)：池满时明确报"繁忙"，而不是无限阻塞
# 用法不变：`with _connect() as conn:` 自动 commit/rollback + 归还连接。
# =====================================================================
import psycopg2.pool
from contextlib import contextmanager

_POOL = None
_POOL_LOCK = threading.Lock()
_CONN_SEM = threading.Semaphore(20)
POOL_TIMEOUT = 5  # 等待空闲连接超时（秒）


def _get_pool():
    global _POOL
    if _POOL is None:
        with _POOL_LOCK:
            if _POOL is None:
                _POOL = psycopg2.pool.ThreadedConnectionPool(5, 20, DB_URL)
    return _POOL


@contextmanager
def _connect():
    """池化连接上下文：acquire 超时明确报错；事务 commit/rollback；finally 归还连接"""
    if not _CONN_SEM.acquire(timeout=POOL_TIMEOUT):
        raise TimeoutError("数据库连接池繁忙（并发超过 20），请稍后再试")
    conn = _get_pool().getconn()
    try:
        yield conn
        conn.commit()
    except BaseException:
        conn.rollback()
        raise
    finally:
        _get_pool().putconn(conn)
        _CONN_SEM.release()


# ==================== 读取事实源 ====================

def load_novels() -> List[Dict]:
    """从 PG 拉小说列表（已上架）"""
    with _connect() as conn:
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute("""
            SELECT id, title, author, chapter_count, total_chars, status
            FROM jf_novel WHERE del_flag = 0 ORDER BY id
        """)
        return list(cur.fetchall())


def load_paragraphs(novel_id: int) -> List[Dict]:
    """拉某本小说的全部自然段（按章节/段落序）"""
    with _connect() as conn:
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute("""
            SELECT p.id, p.chapter_no, p.para_seq, p.content, p.chars
            FROM jf_novel_paragraph p
            WHERE p.novel_id = %s
            ORDER BY p.chapter_no, p.para_seq
        """, (novel_id,))
        return list(cur.fetchall())


# ==================== 写入检索块 ====================

def clear_chunks(novel_id: int):
    """清空某书旧检索块（重build前）"""
    with _connect() as conn:
        cur = conn.cursor()
        cur.execute("DELETE FROM jf_rag_chunk WHERE novel_id = %s", (novel_id,))
        conn.commit()


def save_chunks(chunks: List[Dict], embeddings: Optional[np.ndarray]):
    """
    批量写检索块。chunks 元素: {novel_id, chapter_no, para_seq, chunk_seq,
    content, chars, strategy}
    embeddings: (N, 768) 或 None（未向量化时写 status=0）
    """
    if not chunks:
        return
    with _connect() as conn:
        cur = conn.cursor()
        sql = """
            INSERT INTO jf_rag_chunk
                (novel_id, chapter_no, para_seq, chunk_seq, content, chars,
                 strategy, embedding, status)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        rows = []
        for i, c in enumerate(chunks):
            emb = None
            status = 0
            if embeddings is not None and i < len(embeddings):
                emb = embeddings[i].tolist()
                status = 1
            rows.append((c["novel_id"], c["chapter_no"], c["para_seq"],
                         c["chunk_seq"], c["content"], c["chars"],
                         c.get("strategy", "paragraph"), emb, status))
        psycopg2.extras.execute_batch(cur, sql, rows, page_size=500)
        conn.commit()


# ==================== 检索 ====================

def vector_search(query_vec: np.ndarray, novel_id: int, top_n: int = 24) -> List[Dict]:
    """向量最近邻（余弦距离，pgvector HNSW）"""
    with _connect() as conn:
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute("""
            SELECT id, novel_id, chapter_no, para_seq, chunk_seq, content, chars,
                   1 - (embedding <=> %s::vector) AS score
            FROM jf_rag_chunk
            WHERE novel_id = %s AND status = 1
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """, (query_vec.tolist(), novel_id, query_vec.tolist(), top_n))
        rows = list(cur.fetchall())
    # score 已是相似度（0-1），转 float
    for r in rows:
        r["score"] = float(r["score"])
    return rows


def chunk_count(novel_id: Optional[int] = None) -> Dict:
    """索引状态统计（管理端展示）"""
    with _connect() as conn:
        cur = conn.cursor()
        if novel_id is not None:
            cur.execute("""
                SELECT COUNT(*),
                       COUNT(*) FILTER (WHERE status = 1) AS vectorized
                FROM jf_rag_chunk WHERE novel_id = %s
            """, (novel_id,))
            total, vec = cur.fetchone()
            return {"novel_id": novel_id, "total": total, "vectorized": vec}
        cur.execute("""
            SELECT novel_id, COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE status = 1) AS vectorized
            FROM jf_rag_chunk GROUP BY novel_id ORDER BY novel_id
        """)
        return {"novels": [dict(zip(["novel_id", "total", "vectorized"], r))
                           for r in cur.fetchall()]}


def has_index(novel_id: int) -> bool:
    """该书是否已有向量化块（问答前检查）"""
    with _connect() as conn:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM jf_rag_chunk WHERE novel_id=%s AND status=1",
                    (novel_id,))
        return cur.fetchone()[0] > 0


def get_active_prompt(code: str) -> Optional[str]:
    """读取 LLM 提示词注册表中某场景的当前生效 system prompt（status=1）。
    供各 LLM 调用点使用：改提示词保存后，下次调用即读到新内容（立即生效）。
    无记录返回 None，调用方回退内置默认。
    异常安全：DB 不可达/表缺失时返回 None（不冒泡），保住调用方的内置兜底降级链。
    """
    try:
        with _connect() as conn:
            cur = conn.cursor()
            cur.execute("SELECT system_prompt FROM ai_prompt WHERE code=%s AND status=1 LIMIT 1",
                        (code,))
            row = cur.fetchone()
            return row[0] if row else None
    except Exception:
        logger.warning("读取提示词注册表失败（code=%s），回退内置默认", code)
        return None


def load_indexed_chunks(novel_id: int) -> List[Dict]:
    """读某书全部已向量化块（BM25 语料用），含 id/chapter_no/para_seq/content"""
    with _connect() as conn:
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute("""
            SELECT id, novel_id, chapter_no, para_seq, chunk_seq, content, chars
            FROM jf_rag_chunk
            WHERE novel_id = %s AND status = 1
            ORDER BY chapter_no, para_seq, chunk_seq
        """, (novel_id,))
        rows = list(cur.fetchall())
    for r in rows:
        r["id"] = int(r["id"])
        r["chapter_no"] = int(r["chapter_no"])
    return rows
