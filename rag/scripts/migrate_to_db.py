"""迁移现有 chapters/ + chunks/ 到 data.db"""
import sys
sys.path.insert(0, 'src')

import json
import shutil
from pathlib import Path
from database import get_db_path, init_db, save_chapters, save_chunks

NOVELS_DIR = Path("novels")

for novel_dir in NOVELS_DIR.iterdir():
    if not novel_dir.is_dir():
        continue
    meta_file = novel_dir / "meta.json"
    if not meta_file.exists():
        continue

    print(f"迁移: {novel_dir.name}")
    db_path = get_db_path(str(novel_dir))
    init_db(db_path)

    # 读取章节
    chapters_dir = novel_dir / "chunks"  # 旧结构
    chapters = []
    ch_dir = novel_dir / "chapters"
    if ch_dir.exists():
        for f in sorted(ch_dir.glob("*.json")):
            with open(f, 'r', encoding='utf-8') as fp:
                data = json.load(fp)
            chapters.append({
                "index": len(chapters),
                "title": data["title"],
                "content": data["content"],
                "chars": len(data["content"]),
            })
    save_chapters(db_path, chapters)
    print(f"  章节: {len(chapters)}")

    # 读取切块
    result_file = novel_dir / "chunks" / "fixed" / "result.json"
    if result_file.exists():
        with open(result_file, 'r', encoding='utf-8') as f:
            chunks = json.load(f)
        save_chunks(db_path, chunks, strategy="fixed")
        print(f"  切块: {len(chunks)}")

    # 清理旧目录
    if ch_dir.exists():
        shutil.rmtree(ch_dir)
    chunks_dir = novel_dir / "chunks"
    if chunks_dir.exists():
        shutil.rmtree(chunks_dir)
    print(f"  旧目录已清理")

print("\n完成。")
