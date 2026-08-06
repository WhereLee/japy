"""验证 SQLite 数据"""
import sys
sys.path.insert(0, 'src')
from database import get_db_path, load_chunks, load_chapters, get_chunk_count, get_strategies
from pathlib import Path

novel_dir = Path("novels/龙族2·悼亡者之瞳")
db_path = get_db_path(str(novel_dir))

print(f"策略: {get_strategies(db_path)}")
print(f"切块数: {get_chunk_count(db_path, 'fixed')}")

chapters = load_chapters(db_path)
print(f"章节数: {len(chapters)}")
print(f"第1章: {chapters[1]['title']} ({chapters[1]['chars']}字)")

chunks = load_chunks(db_path, 'fixed')
print(f"\n前3块:")
for c in chunks[:3]:
    print(f"  #{c['chunk_id']} [{c['chapter_title'][:10]}] {c['content'][:40]}...")

print(f"\n按章查询第5幕:")
from database import load_chunks_by_chapter
ch5 = load_chunks_by_chapter(db_path, 5)
print(f"  第5幕共 {len(ch5)} 块")
