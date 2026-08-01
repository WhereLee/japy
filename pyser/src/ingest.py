"""入库主管线：txt → 章节检测 → 文件产出 → 切块 → 向量化 → PostgreSQL"""
import sys
import json
import time
import shutil
import argparse
from pathlib import Path
from datetime import datetime

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src import config
from src.chapter import read_novel, split_chapters, detect_encoding
from src.chunker import chunk_chapter
from src.embedder import Embedder
from src import store


def ingest(file_path: str, novel_keyword: str):
    """执行完整入库流程"""
    t0 = time.time()
    file_path = Path(file_path)

    # 1. 查找小说 ID
    novel = store.get_novel_by_title(novel_keyword)
    if not novel:
        print(f"[ERROR] 数据库中未找到匹配 '{novel_keyword}' 的小说")
        return
    novel_id = novel['id']
    novel_title = novel['title']
    print(f"[1/6] 目标小说: {novel_title} (id={novel_id})")

    # 2. 读取 + 章节检测
    print(f"[2/6] 读取文件: {file_path.name}")
    encoding = detect_encoding(str(file_path))
    text = read_novel(str(file_path))
    chapters = split_chapters(text)
    print(f"      编码: {encoding} | 章节: {len(chapters)}")

    # 3. 产出 txt/ 目录结构
    print("[3/6] 写入 txt/ 目录...")
    txt_dir = _write_txt_output(file_path, novel_title, encoding, text, chapters)
    print(f"      输出: {txt_dir}")

    # 4. 切块
    print("[4/6] 切块中...")
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

    # 5. 向量化
    print("[5/6] 向量化中（bge-base-zh-v1.5）...")
    embedder = Embedder(config.MODEL_PATH)
    texts = [c['content'] for c in all_chunks]
    embeddings = embedder.embed(texts, batch_size=config.EMBED_BATCH_SIZE)
    print(f"      向量维度: {embeddings.shape}")

    # 6. 写入 PostgreSQL
    print("[6/6] 写入 PostgreSQL...")
    store.clear_novel_chunks(novel_id)
    store.insert_chunks(novel_id, all_chunks, embeddings)
    store.update_novel_stats(novel_id, len(chapters), len(all_chunks))

    elapsed = time.time() - t0
    print(f"\n[DONE] {len(chapters)} 章 / {len(all_chunks)} 块 / 耗时 {elapsed:.1f}s")


def _write_txt_output(
    file_path: Path,
    novel_title: str,
    encoding: str,
    text: str,
    chapters: list,
) -> Path:
    """
    产出 txt/{novel_title}/ 目录：
    - source.txt: UTF-8 原件复制
    - manifest.json: 元数据清单
    - 001_title.json ... : 按章拆分的 JSON 文件
    """
    # 目录名：用小说标题（去除不安全字符）
    safe_name = "".join(c for c in novel_title if c not in r'\/:*?"<>|')
    out_dir = config.PROJECT_ROOT / "txt" / safe_name
    out_dir.mkdir(parents=True, exist_ok=True)

    # source.txt（UTF-8 统一编码）
    source_path = out_dir / "source.txt"
    source_path.write_text(text, encoding='utf-8')

    # 按章拆分（JSON：title + content）
    chapter_files = []
    for ch in chapters:
        idx = ch['index'] + 1
        # 文件名：序号_标题前20字
        safe_title = "".join(c for c in ch['title'][:20] if c not in r'\/:*?"<>|')
        filename = f"{idx:03d}_{safe_title}.json"
        chapter_data = {"title": ch['title'], "content": ch['content']}
        (out_dir / filename).write_text(
            json.dumps(chapter_data, ensure_ascii=False, indent=2),
            encoding='utf-8'
        )
        chapter_files.append({
            "no": idx,
            "title": ch['title'],
            "file": filename,
            "chars": ch['chars'],
        })

    # manifest.json
    manifest = {
        "novel_title": novel_title,
        "source_file": file_path.name,
        "source_encoding": encoding,
        "chapter_count": len(chapters),
        "total_chars": sum(ch['chars'] for ch in chapters),
        "processed_at": datetime.now().isoformat(timespec='seconds'),
        "chapters": chapter_files,
    }
    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding='utf-8'
    )

    return out_dir


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='小说入库管线')
    parser.add_argument('--file', required=True, help='txt 文件路径')
    parser.add_argument('--novel', required=True, help='小说关键词（匹配 novel 表）')
    args = parser.parse_args()

    if not Path(args.file).exists():
        print(f"[ERROR] 文件不存在: {args.file}")
        sys.exit(1)

    ingest(args.file, args.novel)
