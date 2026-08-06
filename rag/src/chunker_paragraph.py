"""
paragraph 切块策略（用户设计）：
- 自然段 ≤ 500 字 → 整段 = 1 个 chunk（绝大多数，模型 token 上限内）
- 自然段 > 500 字 → 二分切块（切出近似等大的两段，避免极小块），递归直到 ≤500
- 二分切分点避开"带引号的句子"：若切分点落在含 “” 的句子上，
  则把该句完整保留到一侧（选更接近字数中点的一侧）
- 极端情况（长段全是带引号句子）→ 退化句子边界切，保证可终止

与 fixed/sentence 并存，strategy="paragraph"
"""
import re
from typing import List, Dict

TARGET_SIZE = 500

# 句子边界：仅在结束标点后切分；若后一个字符是右引号则不切（引号内不切断）
# 例：“你还好吗？”他说。 → ？后是” → 不切；整句保留 ✓
SENTENCE_SPLIT = re.compile(r'(?<=[。！？…])(?=[^"\'”’』」】\s])')
# 引号检测
QUOTE_PAIRS = re.compile(r'[“”‘’「」『』]')


def _split_sentences(text: str) -> List[str]:
    """按句子边界断句（引号保护：？” 后才是边界）"""
    if not text or not text.strip():
        return []
    parts = SENTENCE_SPLIT.split(text)
    return [p for p in parts if p.strip()]


def _has_quote(s: str) -> bool:
    return bool(QUOTE_PAIRS.search(s))


def _split_paragraph_binary(para: str) -> List[str]:
    """
    长自然段二分切块：
    1. 断句
    2. 找字数中点对应的句子
    3. 若该句带引号 → 前移到上一句后 / 后移到这句后（取更接近中点者）
    4. 递归直到每块 ≤ TARGET_SIZE
    """
    if len(para) <= TARGET_SIZE:
        return [para]

    sentences = _split_sentences(para)
    if not sentences:
        # 无句子边界 → 硬切兜底（几乎不出现）
        return [para[i:i + TARGET_SIZE] for i in range(0, len(para), TARGET_SIZE)]

    # 逐句累加找中点
    half = len(para) / 2
    acc = 0
    split_idx = len(sentences) - 1  # 默认最后切
    for i, s in enumerate(sentences):
        acc += len(s)
        if acc >= half:
            split_idx = i
            break

    # 引号保护：切分点句子若带引号，尝试移到前一句或后一句
    candidate = split_idx
    if _has_quote(sentences[split_idx]):
        # 切在 split_idx 句子之后
        after = split_idx + 1
        # 切在 split_idx-1 句子之后（前移）
        before = split_idx
        if before > 0:
            # 前移后切分点句子的完整保留到后块；比较两种切法离中点的距离
            acc_before = sum(len(s) for s in sentences[:before])
            acc_after = sum(len(s) for s in sentences[:after])
            if abs(acc_before - half) <= abs(acc_after - half):
                candidate = before
            else:
                candidate = after
        else:
            candidate = after

    left = "".join(sentences[:candidate])
    right = "".join(sentences[candidate:])
    if not left or not right:
        # 边界退化（引号句过长）→ 硬切当前块再递归
        mid = len(para) // 2
        return (_split_paragraph_binary(para[:mid])
                + _split_paragraph_binary(para[mid:]))

    return _split_paragraph_binary(left) + _split_paragraph_binary(right)


def chunk_paragraphs(paragraphs: List[Dict], novel_id: int, strategy: str = "paragraph") -> List[Dict]:
    """
    对自然段列表切块。
    paragraphs: [{chapter_no, para_seq, content, chars}]
    返回 chunk 列表: [{novel_id, chapter_no, para_seq, chunk_seq, content, chars, strategy}]
    """
    chunks = []
    for p in paragraphs:
        content = p["content"].strip()
        if not content:
            continue
        if len(content) <= TARGET_SIZE:
            chunks.append({
                "novel_id": novel_id,
                "chapter_no": p["chapter_no"],
                "para_seq": p["para_seq"],
                "chunk_seq": 0,
                "content": content,
                "chars": len(content),
                "strategy": strategy,
            })
        else:
            pieces = _split_paragraph_binary(content)
            for i, piece in enumerate(pieces):
                chunks.append({
                    "novel_id": novel_id,
                    "chapter_no": p["chapter_no"],
                    "para_seq": p["para_seq"],
                    "chunk_seq": i,
                    "content": piece,
                    "chars": len(piece),
                    "strategy": strategy,
                })
    return chunks
