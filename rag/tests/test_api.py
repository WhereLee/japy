"""快速验证 API"""
import urllib.request, json

resp = urllib.request.urlopen('http://127.0.0.1:8000/api/novels')
data = json.loads(resp.read())
novels = data['novels']
print(f"{len(novels)} novel(s)")
for n in novels:
    print(f"  {n['name']}: {n.get('chapter_count','-')}ch / {n.get('chunk_count','-')} chunks / {n.get('status')}")
