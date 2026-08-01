"""
章节检测模块：模式匹配 → 验证过滤 → 降级兜底

设计原则：
- 宽进严出：第一层尽量多收集候选，后续验证过滤
- 容错降级：检测失败不阻断流程，整本按固定字数分章
- 可扩展：ALL_PATTERNS 列表可随时追加新模式
"""
import re
from typing import List, Dict, Tuple

# ============================================================
# 模式库
# ============================================================

# 第一组：标准"第X章/节/回/卷..."
P_STANDARD = re.compile(
    r'^[\s\u3000]*'
    r'(?:正文[\s\u3000·]*)?'                    # 可选"正文"前缀
    r'第[零〇一二三四五六七八九十百千万\d]+'     # 第X
    r'[章节幕回卷集部篇折出]'                    # 量词
    r'[\s\u3000:：.．、\-—·]*'                  # 可选分隔符
    r'.{0,40}$'                                 # 可选标题
)

# 第二组：特殊标记词
P_SPECIAL = re.compile(
    r'^[\s\u3000]*'
    r'(?:序幕|序章|序曲|序言|楔子|引子|尾声|后记|终章|终曲|番外|结语|补记|跋)'
    r'[\s\u3000:：.．、\-—·]*'
    r'.{0,40}$'
)

# 第三组：卷/部/篇级别
P_JUAN_BU = re.compile(
    r'^[\s\u3000]*'
    r'(?:'
    r'第[零〇一二三四五六七八九十百千万\d]+[卷部篇]'  # 第X卷/部/篇
    r'|[零〇一二三四五六七八九十]+卷'                  # X卷（无"第"）
    r'|[上中下][卷部篇]'                              # 上卷/中篇/下部
    r')'
    r'[\s\u3000:：.．、\-—·]*'
    r'.{0,40}$'
)

# 第四组：纯数字编号（"1. 标题" "01、标题"）
P_NUMERIC = re.compile(
    r'^[\s\u3000]*'
    r'\d{1,4}'                          # 1-4位数字
    r'[\s.、．:：\-—]+'                # 必须有分隔符
    r'.{1,40}$'                         # 后面有标题文字
)

# 第五组：无"第"字的中文数字+量词（"一章 xxx"）
P_NO_DI = re.compile(
    r'^[\s\u3000]*'
    r'[零〇一二三四五六七八九十百千]+[章节回]'
    r'[\s\u3000:：.．、\-—·]+'         # 必须有分隔符
    r'.{1,40}$'
)

# 第六组：英文/混合格式
P_ENGLISH = re.compile(
    r'^[\s]*'
    r'(?:Chapter|CHAPTER|Part|PART|Section|Prologue|Epilogue|Interlude)'
    r'[\s\d:：.]*'
    r'.{0,40}$',
    re.IGNORECASE
)

# 第七组：括号格式 【第一章】「第一章」[第一章]
P_BRACKET = re.compile(
    r'^[\s\u3000]*'
    r'[【「\[]'
    r'(?:第[零〇一二三四五六七八九十百千万\d]+[章节幕回卷集部篇折出]'
    r'|(?:序幕|楔子|尾声|后记|终章|番外))'
    r'.{0,30}'
    r'[】」\]]'
    r'.{0,20}$'
)

# 汇总
ALL_PATTERNS: List[Tuple[str, re.Pattern]] = [
    ("standard",    P_STANDARD),
    ("special",     P_SPECIAL),
    ("juan_bu",     P_JUAN_BU),
    ("numeric",     P_NUMERIC),
    ("no_di",       P_NO_DI),
    ("english",     P_ENGLISH),
    ("bracket",     P_BRACKET),
]


# ============================================================
# 编码检测
# ============================================================

def detect_encoding(file_path: str) -> str:
    """检测文件编码：UTF-8 BOM > UTF-8 > GBK"""
    with open(file_path, 'rb') as f:
        raw = f.read(4096)
    if raw[:3] == b'\xef\xbb\xbf':
        return 'utf-8-sig'
    try:
        raw.decode('utf-8')
        return 'utf-8'
    except UnicodeDecodeError:
        return 'gbk'


def read_novel(file_path: str) -> str:
    """读取小说文件，自动检测编码，统一换行符"""
    enc = detect_encoding(file_path)
    with open(file_path, 'r', encoding=enc, errors='replace') as f:
        text = f.read()
    # 统一换行符
    return text.replace('\r\n', '\n').replace('\r', '\n')


# ============================================================
# 章节标题判定
# ============================================================

def is_chapter_title(line: str) -> Tuple[bool, str]:
    """
    判断一行是否为章节标题。
    返回 (是否命中, 模式名)
    """
    stripped = line.strip()
    if not stripped or len(stripped) > 50:
        return False, ""
    for name, pattern in ALL_PATTERNS:
        if pattern.match(stripped):
            return True, name
    return False, ""


# ============================================================
# 章节切分
# ============================================================

def split_chapters(text: str) -> List[Dict]:
    """
    将全文切分为章节列表。
    返回: [{"index": 0, "title": "序幕 ...", "content": "...", "chars": 1234}, ...]
    """
    lines = text.split('\n')
    candidates = []  # (line_number, title, pattern_name)

    for i, line in enumerate(lines):
        hit, pattern_name = is_chapter_title(line)
        if hit:
            candidates.append((i, line.strip(), pattern_name))

    # 验证过滤
    candidates = _validate_candidates(candidates, lines)

    # 如果验证后无候选，降级
    if not candidates:
        return _fallback_split(text)

    # 按候选切分内容
    chapters = []
    for idx, (line_no, title, _) in enumerate(candidates):
        # 内容从标题下一行到下一个标题前
        start = line_no + 1
        end = candidates[idx + 1][0] if idx + 1 < len(candidates) else len(lines)
        content = '\n'.join(lines[start:end]).strip()
        if content:
            chapters.append({
                "index": len(chapters),
                "title": title,
                "content": content,
                "chars": len(content),
            })

    # 处理第一个标题之前的内容（如书名、简介）
    if candidates[0][0] > 0:
        pre_content = '\n'.join(lines[:candidates[0][0]]).strip()
        if pre_content and len(pre_content) > 200:
            chapters.insert(0, {
                "index": 0,
                "title": "前言",
                "content": pre_content,
                "chars": len(pre_content),
            })
            # 重新编号
            for i, ch in enumerate(chapters):
                ch["index"] = i

    return chapters if chapters else _fallback_split(text)


# ============================================================
# 验证层
# ============================================================

def _validate_candidates(candidates: List, lines: List[str]) -> List:
    """
    二次验证：
    1. 目录页检测：前20行内超过5个命中 → 跳过这些（是目录不是正文）
    2. 最小间距：两个章节之间至少200字正文
    3. 密度异常：如果章节数 > 总行数/5，大概率误判
    """
    if not candidates:
        return []

    total_lines = len(lines)

    # 目录页检测：前20行密集命中
    early_hits = [c for c in candidates if c[0] < 20]
    if len(early_hits) >= 5:
        # 跳过前20行的候选（是目录）
        candidates = [c for c in candidates if c[0] >= 20]
        if not candidates:
            return []

    # 密度异常检测
    if len(candidates) > total_lines / 5:
        # 只保留 standard/special/bracket 模式的命中（更可靠）
        reliable = {c for c in candidates if c[2] in ("standard", "special", "bracket", "juan_bu")}
        if len(reliable) >= 3:
            candidates = sorted(reliable, key=lambda x: x[0])

    # 最小间距过滤：两章之间至少200字
    filtered = [candidates[0]]
    for i in range(1, len(candidates)):
        prev_line = filtered[-1][0]
        curr_line = candidates[i][0]
        between_text = '\n'.join(lines[prev_line + 1:curr_line])
        if len(between_text.strip()) >= 200:
            filtered.append(candidates[i])
        # 如果间距不够，跳过当前候选（可能是误判）

    return filtered


# ============================================================
# 降级策略
# ============================================================

def _fallback_split(text: str, chars_per_chapter: int = 20000) -> List[Dict]:
    """降级：按固定字数切分（在段落边界断开）"""
    chapters = []
    paragraphs = text.split('\n')
    current = ""
    ch_idx = 0

    for para in paragraphs:
        current += para + '\n'
        if len(current) >= chars_per_chapter:
            chapters.append({
                "index": ch_idx,
                "title": f"第{ch_idx + 1}部分",
                "content": current.strip(),
                "chars": len(current.strip()),
            })
            ch_idx += 1
            current = ""

    if current.strip():
        chapters.append({
            "index": ch_idx,
            "title": f"第{ch_idx + 1}部分",
            "content": current.strip(),
            "chars": len(current.strip()),
        })

    return chapters
