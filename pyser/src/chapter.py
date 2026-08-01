"""章节检测：识别小说文本中的章节边界"""
import re
from typing import List, Dict

# 章节标题模式（宽进严出）
CHAPTER_PATTERNS = [
    # 第X章/节/回/卷
    re.compile(r'^[\s\u3000]*(?:正文[\s\u3000]+)?第[零〇一二三四五六七八九十百千万\d]+[章节幕回卷集部篇]'),
    # 序幕/序章/楔子/尾声/后记
    re.compile(r'^[\s\u3000]*(?:序幕|序章|序曲|楔子|引子|尾声|后记|终章|番外)'),
    # 纯数字章节 "1." "01"
    re.compile(r'^[\s\u3000]*\d{1,3}[\s.、．]+.{0,30}$'),
]


def detect_encoding(file_path: str) -> str:
    """检测文件编码"""
    with open(file_path, 'rb') as f:
        raw = f.read(4096)
    # 尝试 UTF-8 BOM
    if raw[:3] == b'\xef\xbb\xbf':
        return 'utf-8-sig'
    # 尝试 UTF-8
    try:
        raw.decode('utf-8')
        return 'utf-8'
    except UnicodeDecodeError:
        pass
    # 默认 GBK
    return 'gbk'


def read_novel(file_path: str) -> str:
    """读取小说文件，自动检测编码"""
    enc = detect_encoding(file_path)
    with open(file_path, 'r', encoding=enc, errors='replace') as f:
        return f.read()


def is_chapter_title(line: str) -> bool:
    """判断一行是否为章节标题"""
    stripped = line.strip()
    if not stripped or len(stripped) > 50:
        return False
    for pat in CHAPTER_PATTERNS:
        if pat.match(stripped):
            return True
    return False


def split_chapters(text: str) -> List[Dict]:
    """
    将全文切分为章节列表。
    返回: [{"index": 0, "title": "序幕 ...", "content": "..."}, ...]
    如果检测不到章节，整本作为单章。
    """
    lines = text.split('\n')
    chapters = []
    current_title = ""
    current_lines = []

    for line in lines:
        if is_chapter_title(line):
            # 保存上一章
            if current_lines or current_title:
                content = '\n'.join(current_lines).strip()
                if content:
                    chapters.append({
                        "index": len(chapters),
                        "title": current_title,
                        "content": content
                    })
            current_title = line.strip()
            current_lines = []
        else:
            current_lines.append(line)

    # 最后一章
    content = '\n'.join(current_lines).strip()
    if content:
        chapters.append({
            "index": len(chapters),
            "title": current_title,
            "content": content
        })

    # 降级：如果只检测到 0-1 章，按固定字数分章
    if len(chapters) <= 1 and len(text) > 10000:
        return _fallback_split(text)

    return chapters


def _fallback_split(text: str, chars_per_chapter: int = 20000) -> List[Dict]:
    """降级：按固定字数切分"""
    chapters = []
    for i in range(0, len(text), chars_per_chapter):
        chunk = text[i:i + chars_per_chapter]
        chapters.append({
            "index": len(chapters),
            "title": f"第{len(chapters) + 1}部分",
            "content": chunk
        })
    return chapters
