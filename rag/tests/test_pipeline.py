"""测试新管线：编码检测 → 章节检测 → 切块"""
import sys
sys.path.insert(0, 'src')

from encoding_detector import read_file_auto_encoding
from chapter_detector import detect_chapters
from chunker import chunk_chapters

# 测试现有小说
txt_path = "novels/txt/龙族2·悼亡者之瞳.txt"

print("=" * 50)
print("  管线测试")
print("=" * 50)

# 1. 编码检测 + 读取
text, encoding, confidence = read_file_auto_encoding(txt_path)
print(f"\n[编码] {encoding} (置信度: {confidence:.2f})")
print(f"[字数] {len(text):,}")

# 2. 章节检测
result = detect_chapters(text)
print(f"\n[章节] {result.message}")
print(f"[模式] {result.pattern_name}")
print(f"[数量] {len(result.chapters)} 章")
print("\n前5章:")
for ch in result.chapters[:5]:
    print(f"  {ch['index']:2d}. {ch['title'][:30]:30s} {ch['chars']:>6,} 字")
print("...")
for ch in result.chapters[-2:]:
    print(f"  {ch['index']:2d}. {ch['title'][:30]:30s} {ch['chars']:>6,} 字")

# 3. 切块
chunks = chunk_chapters(result.chapters)
print(f"\n[切块] 共 {len(chunks)} 块")
print(f"[大小] 平均 {sum(c['chars'] for c in chunks)//len(chunks)} 字/块")
print(f"[范围] {min(c['chars'] for c in chunks)} ~ {max(c['chars'] for c in chunks)} 字")

# 抽样
print("\n前3块预览:")
for c in chunks[:3]:
    preview = c['content'][:60].replace('\n', ' ')
    print(f"  #{c['chunk_id']} [{c['chapter_title'][:10]}] {preview}...")

print("\n" + "=" * 50)
print("  测试通过")
print("=" * 50)
