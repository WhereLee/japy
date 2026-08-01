"""PostgreSQL 存储层：novel_chunk 表读写"""
import psycopg2
import numpy as np
from typing import List, Dict, Optional
from . import config


def get_conn():
    return psycopg2.connect(
        host=config.PG_HOST,
        port=config.PG_PORT,
        dbname=config.PG_DB,
        user=config.PG_USER,
        password=config.PG_PASSWORD,
    )


def clear_novel_chunks(novel_id: int):
    """清除某本小说的所有切块（重新导入前调用）"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM novel_chunk WHERE novel_id = %s", (novel_id,))
        conn.commit()
    finally:
        conn.close()


def insert_chunks(novel_id: int, chunks: List[Dict], embeddings: np.ndarray):
    """
    批量插入切块。
    chunks: [{"chapter_no": 1, "chapter_title": "...", "seq_in_chapter": 0, "content": "..."}]
    embeddings: (N, 768) numpy array
    """
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            for i, chunk in enumerate(chunks):
                vec_str = '[' + ','.join(f'{v:.6f}' for v in embeddings[i]) + ']'
                cur.execute(
                    """INSERT INTO novel_chunk (novel_id, chapter_no, chapter_title, seq_in_chapter, content, embedding)
                       VALUES (%s, %s, %s, %s, %s, %s::vector)""",
                    (novel_id, chunk['chapter_no'], chunk['chapter_title'],
                     chunk['seq_in_chapter'], chunk['content'], vec_str)
                )
        conn.commit()
    finally:
        conn.close()


def update_novel_stats(novel_id: int, chapter_count: int, chunk_count: int):
    """更新 novel 表的统计字段"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """UPDATE novel SET chapter_count = %s, chunk_count = %s, import_status = 1
                   WHERE id = %s""",
                (chapter_count, chunk_count, novel_id)
            )
        conn.commit()
    finally:
        conn.close()


def get_novel_by_title(title: str) -> Optional[Dict]:
    """按标题模糊查找小说"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, title FROM novel WHERE title LIKE %s", (f'%{title}%',))
            row = cur.fetchone()
            if row:
                return {"id": row[0], "title": row[1]}
    finally:
        conn.close()
    return None


def search_similar(novel_id: int, query_vector: np.ndarray, top_k: int = 10) -> List[Dict]:
    """向量相似度检索"""
    vec_str = '[' + ','.join(f'{v:.6f}' for v in query_vector) + ']'
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """SELECT id, chapter_no, chapter_title, seq_in_chapter, content,
                          1 - (embedding <=> %s::vector) AS similarity
                   FROM novel_chunk
                   WHERE novel_id = %s
                   ORDER BY embedding <=> %s::vector
                   LIMIT %s""",
                (vec_str, novel_id, vec_str, top_k)
            )
            rows = cur.fetchall()
            return [
                {
                    "id": r[0], "chapter_no": r[1], "chapter_title": r[2],
                    "seq_in_chapter": r[3], "content": r[4], "similarity": float(r[5])
                }
                for r in rows
            ]
    finally:
        conn.close()
