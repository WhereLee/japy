# Function Calling 工具设计

## 1. OpenAI Function Calling 格式

### 1.1 工具定义

```python
tools = [
    {
        "type": "function",
        "function": {
            "name": "query_annotations",
            "description": "查询批注数据，支持按章节、用户、类型、时间范围筛选。当管理员想了解特定小说或章节的批注情况时使用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "novel_id": {
                        "type": "integer",
                        "description": "小说ID。天龙八部=1，我的隔壁有女鬼=2，龙族2=3"
                    },
                    "chapter_id": {
                        "type": "integer",
                        "description": "章节ID，可选"
                    },
                    "type": {
                        "type": "integer",
                        "enum": [0, 1],
                        "description": "0=普通批注 1=数据校验"
                    },
                    "date_from": {
                        "type": "string",
                        "description": "起始日期 YYYY-MM-DD，可选"
                    },
                    "keyword": {
                        "type": "string",
                        "description": "关键词搜索，可选"
                    }
                },
                "required": []
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "search_similar_annotations",
            "description": "语义搜索与给定文本相似的批注。当需要找内容相似的批注时使用，比如查找重复的数据校验反馈。",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "要搜索的文本内容"
                    },
                    "top_k": {
                        "type": "integer",
                        "description": "返回数量，默认5"
                    },
                    "type_filter": {
                        "type": "integer",
                        "enum": [0, 1],
                        "description": "限制批注类型，可选"
                    }
                },
                "required": ["query"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_user_stats",
            "description": "获取用户的统计数据和行为画像。当管理员想了解某个用户的情况时使用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "user_id": {
                        "type": "integer",
                        "description": "用户ID"
                    }
                },
                "required": ["user_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "generate_report",
            "description": "生成数据校验汇总报告，按问题类型和章节聚类。当管理员想了解数据质量总体情况时使用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "novel_id": {
                        "type": "integer",
                        "description": "小说ID，可选，不指定则汇总所有小说"
                    },
                    "date_range": {
                        "type": "string",
                        "enum": ["7d", "30d", "all"],
                        "description": "时间范围"
                    }
                },
                "required": []
            }
        }
    }
]
```

### 1.2 调用示例

```python
from openai import OpenAI
import json

client = OpenAI()

response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": "你是小说社区的管理助手，帮助管理员查询和分析社区数据。"},
        {"role": "user", "content": "龙族第3章最近有没有人反馈排版问题？"}
    ],
    tools=tools,
    tool_choice="auto"
)

message = response.choices[0].message

if message.tool_calls:
    for tool_call in message.tool_calls:
        func_name = tool_call.function.name
        func_args = json.loads(tool_call.function.arguments)
        print(f"调用工具: {func_name}")
        print(f"参数: {func_args}")
        # 输出:
        # 调用工具: query_annotations
        # 参数: {"novel_id": 3, "type": 1}
```

## 2. 工具描述撰写技巧

### 2.1 核心原则

| 原则 | 好的例子 | 差的例子 |
|------|---------|---------|
| 说明**何时使用** | "当管理员想了解特定章节的批注情况时使用" | "查询批注" |
| 说明**参数含义** | "novel_id: 小说ID。天龙八部=1，龙族2=3" | "novel_id: 小说ID" |
| 说明**返回内容** | "返回批注列表，包含内容、用户、时间" | 返回数据 |
| 区分相似工具 | search_similar 用于语义搜索，query_annotations 用于条件筛选 | 都叫"查询" |

### 2.2 Few-shot 示例设计

```python
SYSTEM_PROMPT = """你是小说社区的管理助手。请根据管理员的问题，选择合适的工具来回答。

## 工具选择示例

### 示例1
问题："龙族有多少数据校验批注？"
工具：query_annotations(novel_id=3, type=1)
理由：需要按小说和类型筛选

### 示例2
问题："有没有人反馈过类似的排版问题？"
工具：search_similar_annotations(query="排版问题")
理由：需要语义搜索相似内容

### 示例3
问题："帮我看看用户3的行为记录"
工具：get_user_stats(user_id=3)
理由：需要查询特定用户的数据

### 示例4
问题："这周的数据质量怎么样？"
工具：generate_report(date_range="7d")
理由：需要生成汇总报告
"""
```

## 3. 多工具路由策略

### 3.1 单步调用

LLM 一次决定调用一个工具。

### 3.2 并行调用

LLM 一次决定调用多个独立工具。

```python
# OpenAI 支持并行工具调用
response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "龙族和天龙八部的数据校验批注各有多少？"}],
    tools=tools,
)
# 可能返回两个 tool_calls:
# 1. query_annotations(novel_id=3, type=1)
# 2. query_annotations(novel_id=1, type=1)
```

### 3.3 链式调用

第一个工具的结果决定下一步调用什么。

```
用户："龙族第3章的排版问题严重吗？"
Step 1: query_annotations(novel_id=3, type=1) → 获取数据校验批注
Step 2: 根据结果判断是否需要 → search_similar_annotations(query="排版") → 查找相似问题
Step 3: 综合两个结果生成回答
```

## 4. 错误处理和重试

```python
import asyncio
from typing import Any

class ToolExecutor:
    def __init__(self):
        self.tools = {}
        self.max_retries = 3

    def register(self, name: str, func):
        self.tools[name] = func

    async def execute(self, name: str, args: dict) -> Any:
        if name not in self.tools:
            return {"error": f"Unknown tool: {name}"}
        
        for attempt in range(self.max_retries):
            try:
                result = await self.tools[name](**args)
                return {"success": True, "data": result}
            except Exception as e:
                if attempt == self.max_retries - 1:
                    return {"success": False, "error": str(e)}
                await asyncio.sleep(2 ** attempt)  # 指数退避

# 使用
executor = ToolExecutor()
executor.register("query_annotations", query_annotations_func)
executor.register("search_similar", search_similar_func)
```

## 5. Python 工具实现示例

```python
# 工具函数实现
async def query_annotations(novel_id: int = None, chapter_id: int = None,
                           type: int = None, keyword: str = None,
                           date_from: str = None) -> list[dict]:
    """查询批注数据"""
    # 调用 Java 后端 API
    import httpx
    params = {}
    if novel_id: params["novelId"] = novel_id
    if chapter_id: params["chapterId"] = chapter_id
    if type is not None: params["type"] = type
    
    async with httpx.AsyncClient() as client:
        # 先获取章节列表
        if novel_id and not chapter_id:
            resp = await client.get(f"http://localhost:8081/api/novels/{novel_id}")
            chapters = resp.json()["chapters"]
            all_annotations = []
            for ch in chapters:
                resp = await client.get(f"http://localhost:8081/api/annotations",
                                       params={"chapterId": ch["id"]})
                all_annotations.extend(resp.json())
            # 过滤type
            if type is not None:
                all_annotations = [a for a in all_annotations if a.get("type") == type]
            return all_annotations
        elif chapter_id:
            resp = await client.get(f"http://localhost:8081/api/annotations",
                                   params={"chapterId": chapter_id})
            return resp.json()

async def search_similar_annotations(query: str, top_k: int = 5,
                                     type_filter: int = None) -> list[dict]:
    """语义搜索相似批注"""
    # 调用向量数据库检索
    embedding = embedding_model.encode(query)
    results = vector_store.search(embedding, top_k=top_k)
    return results
```
