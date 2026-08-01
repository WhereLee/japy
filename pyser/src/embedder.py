"""向量化模块：使用 bge-base-zh-v1.5 生成 768 维向量"""
import numpy as np
from typing import List
from pathlib import Path


class Embedder:
    def __init__(self, model_path: str):
        from sentence_transformers import SentenceTransformer
        self.model = SentenceTransformer(str(model_path))

    def embed(self, texts: List[str], batch_size: int = 64) -> np.ndarray:
        """批量向量化，返回 (N, 768) 的 numpy 数组"""
        if not texts:
            return np.array([]).reshape(0, 768)
        embeddings = self.model.encode(
            texts,
            batch_size=batch_size,
            show_progress_bar=len(texts) > 100,
            normalize_embeddings=True,  # L2 归一化，余弦相似度直接用内积
        )
        return embeddings.astype(np.float32)
