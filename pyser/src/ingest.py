"""入库主管线：txt → 章节检测 → 切块 → 向量化 → PostgreSQL"""
import sys
import time
import argparse
from pathlib import Path

# 确保 src 在 path 中
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src import config
from src.chapter import read_novel, split_chapters
from src.chunker import chunk_chapter
from src.embedder import Embedder
from src import store


def ingest(file_path: str, novel_keyword: str):
    """执行完整入库流程"""
    t0 = time.time()

    # 1. 查找小说 ID
    novel = store.get_novel_by_title(novel_keyword)
    if not novel:
        print(f"[ERROR] 数据库中未找到匹配 '{novel_keyword}' 的小说，请先在 novel 表中添加记录")
        return
    novel_id = novel['id']
    print(f"[1/5] 目标小说: {novel['title']} (id={novel_id})")

    # 2. 读取文件 + 章节检测
    print(f"[2/5] 读取文件: {file_path}")
    text = read_novel(file_path)
    chapters = split_chapters(text)
    print(f"      检测到 {len(chapters)} 个章节")

    # 3. 切块
    print("[3/5] 切块中...")
    all_chunks = []
    for ch in chapters:
        chapter_chunks = chunk_chapter(
            ch['content'],
            chunk_size=config.CHUNK_SIZE,
            overlap=config.CHUNK_OVERLAP,
            max_size=config.CHUNK_MAX_SIZE,
        )
        for seq, content in enumerate(chapter_chunks):
            all_chunks.append({
                'chapter_no': ch['index'] + 1,
                'chapter_title': ch['title'][:100],
                'seq_in_chapter': seq,
                'content': content,
            })
    print(f"      共 {len(all_chunks)} 个切块")

    # 4. 向量化
    print("[4/5] 向量化中（bge-base-zh-v1.5）...")
    embedder = Embedder(config.MODEL_PATH)
    texts = [c['content'] for c in all_chunks]
    embeddings = embedder.embed(texts, batch_size=config.EMBED_BATCH_SIZE)
    print(f"      向量维度: {embeddings.shape}")

    # 5. 写入 PostgreSQL
    print("[5/5] 写入 PostgreSQL...")
    store.clear_novel_chunks(novel_id)
    store.insert_chunks(novel_id, all_chunks, embeddings)
    store.update_novel_stats(novel_id, len(chapters), len(all_chunks))

    elapsed = time.time() - t0
    print(f"\n[DONE] 入库完成: {len(chapters)} 章 / {len(all_chunks)} 块 / 耗时 {elapsed:.1f}s")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='小说入库管线')
    parser.add_argument('--file', required=True, help='txt 文件路径')
    parser.add_argument('--novel', required=True, help='小说关键词（用于匹配 novel 表）')
    args = parser.parse_args()

    if not Path(args.file).exists():
        print(f"[ERROR] 文件不存在: {args.file}")
        sys.exit(1)

    ingest(args.file, args.novel)
