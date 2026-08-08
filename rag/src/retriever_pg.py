"""
PG 版混合检索器：向量(PG pgvector) + BM25 + RRF + Rerank
数据源：japy-framework 数据库（jf_novel_paragraph 事实源 → jf_rag_chunk 检索块）
替代旧 retriever.py（ChromaDB 版），对接融入后的新链路。
"""
import logging
import threading
import time
from typing import Dict, List, Optional

import jieba
import numpy as np
from rank_bm25 import BM25Okapi

import pg_store
from config import (
    NOVELS_RAW_DIR, logger,
    VECTOR_TOP_K, BM25_TOP_K, TOP_K, RRF_K,
)
from embedder import Embedder
from dict_builder import load_dict

RERANKER_ONNX_INT8_PATH = NOVELS_RAW_DIR.parent / "models" / "bge-reranker-v2-m3-onnx-int8"

# Reranker 并发控制（全局信号量，最多同时 1 个 Rerank 操作）
# bge-reranker int8 单实例 ~569MB，并发推理会内存暴涨/OOM（与旧 retriever.py 一致）
_rerank_semaphore = threading.Semaphore(1)
RERANK_TIMEOUT = 15  # 等待信号量超时（秒）
RERANK_QUEUE_MAX = 8  # 等待队列上限：超过即明确告知"高峰"，不再静默降级
_rerank_waiting = 0
_rerank_waiting_lock = threading.Lock()


class RerankBusyError(RuntimeError):
    """Rerank 排队超限——上层转友好提示（不再静默降级 RRF）"""

# ---------------------------------------------------------------------------
# 全局单例：Reranker 全进程只加载一份（多书共享，避免每检索器一份 569MB 导致内存爆炸）
# ORT session 线程安全，int8 权重只读，配合 _rerank_semaphore 并发闸门使用
# ---------------------------------------------------------------------------
_reranker = None
_reranker_tokenizer = None
_reranker_lock = threading.Lock()


def _get_global_reranker():
    """懒加载 + 双重检查锁的全局 Reranker（tokenizer + 模型）"""
    global _reranker, _reranker_tokenizer
    if _reranker is None:
        with _reranker_lock:
            if _reranker is None:
                from optimum.onnxruntime import ORTModelForSequenceClassification
                from transformers import AutoTokenizer
                logger.info(f"加载全局 Reranker (ONNX INT8): {RERANKER_ONNX_INT8_PATH}")
                _reranker_tokenizer = AutoTokenizer.from_pretrained(str(RERANKER_ONNX_INT8_PATH))
                _reranker = ORTModelForSequenceClassification.from_pretrained(str(RERANKER_ONNX_INT8_PATH))
    return _reranker, _reranker_tokenizer

logger = logging.getLogger("rag.retriever_pg")


class HybridRetriever:
    """混合检索器（PG 向量源）：每本小说独立实例"""

    def __init__(self, novel_id: int, strategy: str = "paragraph"):
        self.novel_id = novel_id
        self.strategy = strategy

        # === 1. 加载自定义词典 ===
        # 词典以小说名命名，PG 模式用 novel_id 命名（若无则跳过）
        try:
            load_dict(f"novel_{novel_id}")
        except Exception:
            pass

        # === 2. 从 PG 读 chunks（BM25 用）===
        self._chunks = self._load_chunks_from_pg()
        if not self._chunks:
            raise ValueError(f"小说 {novel_id} 无检索块，请先同步索引")

        self._chunk_map = {c["id"]: c for c in self._chunks}

        # === 3. 构建 BM25 索引 ===
        logger.info(f"构建 BM25 索引: novel {novel_id}, {len(self._chunks)} 块...")
        t0 = time.perf_counter()
        self._tokenized_corpus = [jieba.lcut(c["content"]) for c in self._chunks]
        self._bm25 = BM25Okapi(self._tokenized_corpus)
        logger.info(f"BM25 索引构建完成: {time.perf_counter() - t0:.2f}s")

        # === 4. Embedder（全局共享单例，懒加载）===
        self._embedder = None

        # === 5. Reranker（全局共享单例，懒加载）===

    def _load_chunks_from_pg(self) -> List[Dict]:
        """从 PG 读已向量化检索块"""
        rows = pg_store.vector_search(
            query_vec=np.zeros(768), novel_id=self.novel_id, top_n=1)
        if not rows:
            # 无向量块 → 检查是否有待向量化的块（状态0）
            has_pending = pg_store.has_index(self.novel_id)
            if not has_pending:
                raise ValueError(f"小说 {self.novel_id} 未同步索引")
        # 实际加载全部块用于 BM25（含未向量化？不，只读已向量化的）
        return pg_store.load_indexed_chunks(self.novel_id)

    def _get_embedder(self) -> Embedder:
        """向量模型：进程级全局单例（多书共享，避免每书一份 ~100MB）"""
        from embedder import get_shared_embedder
        if self._embedder is None:
            self._embedder = get_shared_embedder(batch_size=1)
        return self._embedder

    def _get_reranker(self):
        """精排模型：进程级全局单例（多书共享，避免每书一份 569MB）"""
        reranker, tokenizer = _get_global_reranker()
        self._reranker_tokenizer = tokenizer
        return reranker

    def _vector_search(self, query: str, top_n: int = VECTOR_TOP_K) -> List[Dict]:
        """向量检索路：query → embedding → pgvector 最近邻 → top_n"""
        query_vec = self._get_embedder().encode_batch([query])[0]
        return pg_store.vector_search(query_vec, self.novel_id, top_n)

    def _bm25_search(self, query: str, top_n: int = BM25_TOP_K) -> List[Dict]:
        """BM25 检索路：query → jieba 分词 → BM25 打分 → top_n"""
        query_tokens = jieba.lcut(query)
        scores = self._bm25.get_scores(query_tokens)
        top_indices = scores.argsort()[::-1][:top_n]
        results = []
        for idx in top_indices:
            if scores[idx] <= 0:
                continue
            c = self._chunks[idx]
            results.append({
                "id": c["id"],
                "content": c["content"],
                "chapter_no": c["chapter_no"],
                "score": float(scores[idx]),
            })
        return results

    def _rerank(self, query: str, candidates: List[Dict], top_k: int = TOP_K) -> List[Dict]:
        """Rerank 精排（bge-reranker ONNX INT8，tokenizer → 模型 → logits[:,0]）
        全局信号量限流 + 队列上限：
        - 队列未满：等待信号量（最多 15s），拿到就精排
        - 队列已满（>8 个请求在等）：抛 RerankBusyError，上层返回友好提示（不静默降级）"""
        if not candidates:
            return []
        # 队列上限检查（进入等待前登记，防并发涌入）
        global _rerank_waiting
        with _rerank_waiting_lock:
            if _rerank_waiting >= RERANK_QUEUE_MAX:
                raise RerankBusyError("问答高峰，精排队列已满，请稍后再试")
            _rerank_waiting += 1
        acquired = _rerank_semaphore.acquire(timeout=RERANK_TIMEOUT)
        if not acquired:
            logger.warning("Rerank 等待超时（15s），排队请求过多")
            with _rerank_waiting_lock:
                _rerank_waiting -= 1
            raise RerankBusyError("问答高峰，精排排队超时，请稍后再试")
        try:
            reranker = self._get_reranker()
            passages = [c["content"] for c in candidates]
            inputs = self._reranker_tokenizer(
                [query] * len(passages), passages,
                padding=True, truncation=True, max_length=512,
                return_tensors="np",
            )
            outputs = reranker(**{k: v for k, v in inputs.items()})
            scores = outputs.logits[:, 0].tolist()  # (N,)
            scored = list(zip(candidates, scores))
            scored.sort(key=lambda x: -x[1])
            return [dict(c, score=float(s)) for c, s in scored[:top_k]]
        except RerankBusyError:
            raise  # 排队超限：不吞掉，上层转友好提示
        except Exception as e:
            logger.warning(f"Rerank 失败，降级 RRF 排序: {e}")
            return candidates[:top_k]
        finally:
            _rerank_semaphore.release()
            with _rerank_waiting_lock:
                _rerank_waiting -= 1

    def search(self, query: str, top_k: int = TOP_K) -> (List[Dict], Dict):
        """
        混合检索：向量 + BM25 → RRF 融合 → Rerank 精排
        返回 (results, meta)
        """
        t0 = time.perf_counter()
        # 1. 两路检索
        vector_results = self._vector_search(query, VECTOR_TOP_K)
        bm25_results = self._bm25_search(query, BM25_TOP_K)

        # 2. RRF 融合
        rrf_scores: Dict[int, float] = {}
        for rank, r in enumerate(vector_results):
            doc_id = r["id"]
            rrf_scores[doc_id] = rrf_scores.get(doc_id, 0) + 1.0 / (RRF_K + rank + 1)
        for rank, r in enumerate(bm25_results):
            doc_id = r["id"]
            rrf_scores[doc_id] = rrf_scores.get(doc_id, 0) + 1.0 / (RRF_K + rank + 1)

        candidates = []
        for doc_id in sorted(rrf_scores.keys(), key=lambda x: -rrf_scores[x]):
            c = self._chunk_map[doc_id]
            candidates.append({
                "id": doc_id,
                "content": c["content"],
                "chapter_no": c["chapter_no"],
                "para_seq": c.get("para_seq"),
                "score": rrf_scores[doc_id],
            })

        # 3. Rerank
        rerank_k = top_k * 2
        candidates = candidates[:rerank_k]
        results = self._rerank(query, candidates, top_k)

        # 4. 置信度
        meta = {
            "top_score": results[0]["score"] if results else 0.0,
            "result_count": len(results),
            "pipeline": f"pgvector{len(vector_results)} + BM25{len(bm25_results)} → RRF → Rerank{len(results)}",
            "elapsed": round(time.perf_counter() - t0, 2),
        }
        return results, meta
