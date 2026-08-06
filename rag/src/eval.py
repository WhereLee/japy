"""
RAG 检索评估脚本：跑评估集 → 关键词命中判定 → 报告
用法：python src/eval.py            # 跑全部 40 题
      python src/eval.py --dim 实体细节   # 单维度
      python src/eval.py --top 8          # top-k 判定（默认 12=TOP_K）
输出：rag/eval/report_{strategy}_{timestamp}.md
"""
import argparse
import json
import sys
import time
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from retriever_pg import HybridRetriever

EVAL_FILE = Path(__file__).resolve().parent.parent / "eval" / "questions.json"
OUT_DIR = Path(__file__).resolve().parent.parent / "eval"


def hit_rate(results, keywords):
    """关键词命中：任一结果 content 含任一期望关键词"""
    if not keywords:
        return True
    for kw in keywords:
        for r in results:
            if kw in r.get("content", ""):
                return True
    return False


def run(novel_id, top_k, dim=None):
    data = json.load(open(EVAL_FILE, encoding="utf-8"))
    qs = [q for q in data["questions"] if dim is None or q["dim"] == dim]

    retriever = HybridRetriever(novel_id)
    rows = []
    hit_total = 0
    t0 = time.time()

    for q in qs:
        results, meta = retriever.search(q["q"], top_k=top_k)
        hit = hit_rate(results, q["keywords"])
        hit_total += 1 if hit else 0
        rows.append({
            "id": q["id"], "dim": q["dim"], "q": q["q"],
            "keywords": q["keywords"], "hit": hit,
            "top_score": round(meta.get("top_score", 0), 3),
            "elapsed": meta.get("elapsed", 0),
            "top_chunks": [r["content"][:40] for r in results[:2]],
        })
        print(f"[{'✓' if hit else '✗'}] {q['id']} {q['q'][:30]}")

    total = len(rows)
    rate = hit_total / total if total else 0
    elapsed = time.time() - t0

    # 分维度统计
    by_dim = Counter()
    by_dim_total = Counter()
    for r in rows:
        by_dim_total[r["dim"]] += 1
        if r["hit"]:
            by_dim[r["dim"]] += 1

    report = [f"# RAG 检索评估报告", f"",
              f"- 小说: {data['novel']} (id={novel_id})", f"- 策略: paragraph, top_k={top_k}",
              f"- 题数: {total} | 命中: {hit_total} | 命中率: {rate:.1%}",
              f"- 总耗时: {elapsed:.1f}s\n",
              f"## 分维度命中率\n",
              f"| 维度 | 命中 | 总数 | 命中率 |",
              f"|------|------|------|--------|"]
    for dim in data["dimensions"]:
        t = by_dim_total[dim]
        h = by_dim[dim]
        report.append(f"| {dim} | {h} | {t} | {h/t:.0%} |" if t else f"| {dim} | - | 0 | - |")

    report += ["\n## 逐题详情\n", "| ID | 维度 | 问题 | 命中 | top_score | 耗时(s) |",
               "|----|------|------|------|-----------|---------|"]
    for r in rows:
        report.append(f"| {r['id']} | {r['dim']} | {r['q'][:40]} | {'✓' if r['hit'] else '✗'} | {r['top_score']} | {r['elapsed']} |")

    ts = time.strftime("%Y%m%d_%H%M%S")
    out = OUT_DIR / f"report_paragraph_top{top_k}_{ts}.md"
    out.write_text("\n".join(report), encoding="utf-8")
    print(f"\n命中率: {rate:.1%} ({hit_total}/{total}) | 报告: {out.name}")
    return rate


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--novel", type=int, default=1)
    ap.add_argument("--top", type=int, default=12)
    ap.add_argument("--dim", type=str, default=None)
    args = ap.parse_args()
    run(args.novel, args.top, args.dim)
