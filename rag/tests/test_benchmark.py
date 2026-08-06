"""三模式对比测试：PyTorch vs ONNX vs ONNX+INT8，同样50块"""
import sys, time
sys.path.insert(0, 'src')

from database import get_db_path, load_chunks
from embedder import Embedder

# 取龙族2 fixed策略的前50块
db_path = get_db_path("novels/龙族2·悼亡者之瞳")
chunks = load_chunks(db_path, "fixed")[:50]
texts = [c["content"] for c in chunks]
print(f"测试数据: 50 块, 平均 {sum(len(t) for t in texts)//50} 字/块")
print("=" * 60)

results = {}

for mode in ["pytorch", "onnx", "onnx_int8"]:
    print(f"\n[{mode}] 加载模型...")
    t0 = time.perf_counter()
    embedder = Embedder(mode=mode, batch_size=48)
    load_time = time.perf_counter() - t0
    print(f"  模型加载: {load_time:.2f}s")

    print(f"  推理 50 块...")
    t1 = time.perf_counter()
    embeddings = embedder.encode_with_pipeline(texts)
    infer_time = time.perf_counter() - t1

    print(f"  推理耗时: {infer_time:.2f}s")
    print(f"  输出维度: {embeddings.shape}")
    results[mode] = {"load": load_time, "infer": infer_time, "total": load_time + infer_time}

    del embedder  # 释放内存

# 汇总
print("\n" + "=" * 60)
print(f"{'模式':<12} {'模型加载':<10} {'推理50块':<10} {'总计':<10} {'相对基线'}")
print("-" * 60)
baseline = results["pytorch"]["infer"]
for mode, r in results.items():
    ratio = r["infer"] / baseline
    print(f"{mode:<12} {r['load']:<10.2f} {r['infer']:<10.2f} {r['total']:<10.2f} {ratio:.2f}x")
print("=" * 60)
