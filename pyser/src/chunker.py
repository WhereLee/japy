"""文本切块：段落边界优先 + 固定字数 + 重叠"""
import re
from typing import List

# 断句标点
SENTENCE_END = re.compile(r'(?<=[。！？…\u201d」』】])')


def chunk_chapter(
    text: str,
    chunk_size: int = 500,
    overlap: int = 100,
    max_size: int = 800,
) -> List[str]:
    """
    对单章文本切块。
    - 优先在段落边界切断
    - 超长段落按句号强制断句
    - 相邻块重叠 overlap 字
    """
    if not text or not text.strip():
        return []

    paragraphs = _split_paragraphs(text)
    chunks = []
    current = ""

    for para in paragraphs:
        # 如果当前块加上这段超过目标大小，先保存当前块
        if current and len(current) + len(para) > chunk_size:
            chunks.append(current.strip())
            # 重叠：取当前块末尾 overlap 字作为下一块开头
            current = current[-overlap:] if overlap > 0 else ""

        current += para

        # 如果单段就超过 max_size，强制断句
        if len(current) > max_size:
            sub_chunks = _force_split(current, max_size)
            # 前面的子块直接加入，最后一个作为 current 继续
            for sc in sub_chunks[:-1]:
                chunks.append(sc.strip())
            current = sub_chunks[-1] if sub_chunks else ""

    if current.strip():
        chunks.append(current.strip())

    return [c for c in chunks if c]


def _split_paragraphs(text: str) -> List[str]:
    """按段落分割（\n、\t开头的行、连续空行）"""
    lines = text.split('\n')
    paragraphs = []
    current = ""

    for line in lines:
        stripped = line.strip()
        if not stripped:
            if current:
                paragraphs.append(current)
                current = ""
            continue
        # \t 开头表示新段落
        if line.startswith('\t') or line.startswith('\u3000\u3000'):
            if current:
                paragraphs.append(current)
            current = stripped
        else:
            current += stripped

    if current:
        paragraphs.append(current)

    return paragraphs


def _force_split(text: str, max_size: int) -> List[str]:
    """强制按句子边界切分超长文本"""
    sentences = SENTENCE_END.split(text)
    chunks = []
    current = ""

    for sent in sentences:
        if not sent:
            continue
        if len(current) + len(sent) > max_size and current:
            chunks.append(current)
            current = sent
        else:
            current += sent

    if current:
        chunks.append(current)

    return chunks
