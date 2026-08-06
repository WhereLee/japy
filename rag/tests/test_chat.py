"""端到端测试：chat API"""
import urllib.request
import json

BASE = "http://127.0.0.1:8000"
NOVEL = "龙族2·悼亡者之瞳"

url = f"{BASE}/api/novels/{urllib.request.quote(NOVEL)}/chat"
data = json.dumps({"message": "路明非的战斗场景有哪些？比如他揍小混混那段", "strategy": "fixed"}).encode()
req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")

print("发送请求...")
resp = urllib.request.urlopen(req, timeout=120)
content = resp.read().decode("utf-8")

# 解析 SSE
lines = content.split("\n")
tokens = []
sources = None
done = False

for line in lines:
    if not line.startswith("data: "):
        continue
    d = json.loads(line[6:])
    if d["type"] == "token":
        tokens.append(d["content"])
    elif d["type"] == "sources":
        sources = d["chunks"]
    elif d["type"] == "done":
        done = True

answer = "".join(tokens)
print(f"\n回答长度: {len(answer)} 字")
print(f"回答前100字: {answer[:100]}")
print(f"\n来源数: {len(sources) if sources else 0}")
if sources:
    for i, s in enumerate(sources[:3]):
        print(f"  #{i+1} {s['chapter_title']} (score: {s['score']})")
        print(f"      {s['content_preview'][:50]}...")
print(f"\nDone: {done}")
print("\n=== 测试通过 ===")
