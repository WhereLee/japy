"""双策略召回对比测试：通过 API 调用，避免本地 ONNX+PyTorch 冲突"""
import urllib.request
import json
import time

BASE = "http://127.0.0.1:8000"
NOVEL = "龙族2·悼亡者之瞳"
OUT_FILE = "test_recall_result.txt"

QUESTIONS = [
    "迈巴赫最终怎么样了？",
    "那个总是穿黑色风衣的冷脸男人是谁？",
    "绘梨衣的结局是什么？",
]

lines = []
def out(s=""):
    lines.append(s)

def ask(question, strategy):
    """call chat API, return (answer, sources)"""
    url = f"{BASE}/api/novels/{urllib.request.quote(NOVEL)}/chat"
    data = json.dumps({"message": question, "strategy": strategy}).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    resp = urllib.request.urlopen(req, timeout=120)
    content = resp.read().decode("utf-8")

    tokens = []
    sources = []
    for line in content.split("\n"):
        if not line.startswith("data: "):
            continue
        d = json.loads(line[6:])
        if d["type"] == "token":
            tokens.append(d["content"])
        elif d["type"] == "sources":
            sources = d["chunks"]
    return "".join(tokens), sources


out("=" * 70)
out(f"  双策略 RAG 问答对比: {NOVEL}")
out("=" * 70)

for qi, q in enumerate(QUESTIONS, 1):
    out(f"\n{'='*70}")
    out(f"  Q{qi}: {q}")
    out(f"{'='*70}")

    for strategy in ["fixed", "sentence"]:
        out(f"\n  --- [{strategy}] ---")
        t0 = time.perf_counter()
        try:
            answer, sources = ask(q, strategy)
            elapsed = time.perf_counter() - t0
            out(f"  耗时: {elapsed:.1f}s")
            out(f"  来源数: {len(sources)}")
            out(f"  来源片段:")
            for i, s in enumerate(sources[:5], 1):
                out(f"    #{i} [{s['chapter_title']}] score={s['score']}")
                out(f"       {s['content_preview'][:60]}")
            out(f"  回答 ({len(answer)}字):")
            out(f"    {answer[:300]}")
            if len(answer) > 300:
                out(f"    ...({len(answer)-300}字省略)")
        except Exception as e:
            out(f"  错误: {e}")

out(f"\n{'='*70}")
out("  对比完成")
out(f"{'='*70}")

with open(OUT_FILE, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))
print(f"done -> {OUT_FILE}")
