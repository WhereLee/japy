"""paragraph 切块策略单测：二分/引号保护/边界"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

from chunker_paragraph import chunk_paragraphs, _split_paragraph_binary, _split_sentences


def test_short_paragraph_whole():
    """≤500 字自然段整段一个块"""
    paras = [{"chapter_no": 1, "para_seq": 1, "content": "短段落内容。" * 10, "chars": 60}]
    chunks = chunk_paragraphs(paras, novel_id=1)
    assert len(chunks) == 1, f"短段应整段一块, got {len(chunks)}"
    assert chunks[0]["chunk_seq"] == 0
    assert chunks[0]["strategy"] == "paragraph"


def test_long_paragraph_split_into_two():
    """510 字 → 二分两块（避免 500+10 极小块）"""
    # 构造 510 字（每句 10 字，51 句）
    text = ("甲乙丙丁戊己庚辛壬癸。" * 51)[:510]
    paras = [{"chapter_no": 1, "para_seq": 1, "content": text, "chars": 510}]
    chunks = chunk_paragraphs(paras, novel_id=1)
    # 二分：两块（也可能因句子边界更多，但每块应 ≤500 且 >0）
    assert len(chunks) >= 2
    for c in chunks:
        assert len(c["content"]) <= 500, f"每块应 ≤500, got {len(c['content'])}"
        assert len(c["content"]) > 0
    # 两块大小应接近（不是 500+10）
    sizes = sorted(len(c["content"]) for c in chunks)
    assert sizes[0] >= 200, f"不应出现极小块, sizes={sizes}"


def test_quote_protection():
    """带引号句子不被切断：切分点落在引号句时整句保留"""
    # 构造：前半 150 字（无引号）+ 引号对话句（15字）+ 后半 360 字 → 总 ~525 超 500
    prefix = "无引号内容。" * 25          # 150 字
    quote = "“你最近还好吗？”他说。"
    suffix = "后续描述。" * 72            # 360 字
    text = prefix + quote + suffix
    assert len(text) > 500, f"应超 500, got {len(text)}"

    pieces = _split_paragraph_binary(text)
    assert len(pieces) >= 2, "应被二分"
    for p in pieces:
        assert len(p) <= 500, f"块应 ≤500, got {len(p)}"
        # 引号句必须完整出现在某一块中（不被拆散）
        if "你最近还好吗" in p:
            assert "他说。" in p, f"引号句被切断: {p[-80:]}..."


def test_quote_only_extreme():
    """极端：长段全是带引号句子 → 仍可终止且每块 ≤500"""
    sentences = []
    for i in range(60):
        sentences.append(f"“第{i}句话的内容就是这样说的。”他说。")
    text = "".join(sentences)  # ~720 字，全带引号
    pieces = _split_paragraph_binary(text)
    for p in pieces:
        assert len(p) <= 500, f"块应 ≤500, got {len(p)}"
        assert len(p) > 0


def test_sentence_split_quote():
    """引号保护：？后是右引号则不切（引号内句号不算句子结束）"""
    # “你还好吗？”他说。 → ？后是”不切 → 整句（对话+叙述）保留
    sents = _split_sentences("他说“你好吗？”然后走了。")
    assert len(sents) == 1, f"引号内？不切分, got {sents}"
    assert sents[0] == "他说“你好吗？”然后走了。"

    # 无引号时正常切分
    sents2 = _split_sentences("他走了。然后他回来了。")
    assert len(sents2) == 2, f"无引号应切分, got {sents2}"


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    passed = 0
    for t in tests:
        try:
            t()
            print(f"✅ {t.__name__}")
            passed += 1
        except AssertionError as e:
            print(f"❌ {t.__name__}: {e}")
    print(f"\n{passed}/{len(tests)} 通过")
