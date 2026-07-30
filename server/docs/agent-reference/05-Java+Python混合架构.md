# Java + Python 混合架构

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端（Vue 3）                          │
│  http://localhost:5173                                   │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              Java 后端（Spring Boot）                     │
│  http://localhost:8081                                   │
│  - 用户/批注/讨论 CRUD                                   │
│  - 数据库（MySQL）                                       │
│  - 调用 Python Agent 服务                                │
└─────────────────────┬───────────────────────────────────┘
                      │ REST / gRPC / Redis
┌─────────────────────▼───────────────────────────────────┐
│            Python Agent 服务（FastAPI）                   │
│  http://localhost:8000                                   │
│  - LangGraph Agent 编排                                  │
│  - RAG 流水线                                            │
│  - LLM 调用                                              │
│  - 向量数据库（Milvus/Chroma）                           │
└─────────────────────────────────────────────────────────┘
```

## 2. 通信方案

### 2.1 REST API（推荐先用这个）

**Python 端（FastAPI）**

```python
# agent-service/app/main.py
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="ReCloud Agent Service")

class ModerationRequest(BaseModel):
    content: str
    user_id: int
    content_type: str  # "annotation" | "comment"

class ModerationResponse(BaseModel):
    label: str          # "safe" | "unsafe" | "uncertain"
    confidence: float
    categories: list[str]
    reason: str

@app.post("/api/agent/moderate", response_model=ModerationResponse)
async def moderate_content(req: ModerationRequest):
    # 规则引擎
    rule_result = rule_engine.evaluate(req.content)
    if rule_result.verdict == "reject":
        return ModerationResponse(
            label="unsafe", confidence=rule_result.confidence,
            categories=["规则命中"], reason=rule_result.reason
        )
    
    # LLM分类
    llm_result = await llm_moderator.moderate(req.content)
    return ModerationResponse(**llm_result)

class SearchRequest(BaseModel):
    query: str
    top_k: int = 5
    novel_id: int = None

@app.post("/api/agent/search")
async def search_annotations(req: SearchRequest):
    results = await rag_pipeline.query(req.query, top_k=req.top_k)
    return {"results": results}

@app.post("/api/agent/chat")
async def chat(request: dict):
    """管理端对话式助手"""
    messages = request.get("messages", [])
    result = await agent.run(messages)
    return {"response": result}
```

**Java 端调用**

```java
// AgentService.java
@Service
public class AgentService {

    @Value("${agent.base-url:http://localhost:8000}")
    private String agentBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public ModerationResult moderate(String content, Long userId, String contentType) {
        String url = agentBaseUrl + "/api/agent/moderate";
        Map<String, Object> body = Map.of(
            "content", content,
            "user_id", userId,
            "content_type", contentType
        );
        return restTemplate.postForObject(url, body, ModerationResult.class);
    }

    public List<Map> searchSimilar(String query, int topK, Long novelId) {
        String url = agentBaseUrl + "/api/agent/search";
        Map<String, Object> body = Map.of(
            "query", query,
            "top_k", topK,
            "novel_id", novelId != null ? novelId : 0
        );
        Map result = restTemplate.postForObject(url, body, Map.class);
        return (List<Map>) result.get("results");
    }
}
```

### 2.2 gRPC（高性能场景）

**Proto 定义**

```protobuf
// proto/agent_service.proto
syntax = "proto3";
package recloud.agent;

service AgentService {
    rpc ModerateContent (ModerationRequest) returns (ModerationResponse);
    rpc SearchAnnotations (SearchRequest) returns (SearchResponse);
    rpc Chat (ChatRequest) returns (stream ChatResponse);  // 流式
}

message ModerationRequest {
    string content = 1;
    int64 user_id = 2;
    string content_type = 3;
}

message ModerationResponse {
    string label = 1;
    float confidence = 2;
    repeated string categories = 3;
    string reason = 4;
}

message SearchRequest {
    string query = 1;
    int32 top_k = 2;
    int64 novel_id = 3;
}

message SearchResponse {
    repeated SearchResult results = 1;
}

message SearchResult {
    string content = 1;
    float score = 2;
    map<string, string> metadata = 3;
}

message ChatRequest {
    repeated Message messages = 1;
}

message ChatResponse {
    string content = 1;
    bool is_tool_call = 2;
    string tool_name = 3;
    string tool_args = 4;
}

message Message {
    string role = 1;
    string content = 2;
}
```

**Python 端实现**

```python
# pip install grpcio grpcio-tools
import grpc
from concurrent import futures
import agent_service_pb2
import agent_service_pb2_grpc

class AgentServicer(agent_service_pb2_grpc.AgentServiceServicer):
    def ModerateContent(self, request, context):
        # 同步调用
        result = moderate_content_sync(request.content)
        return agent_service_pb2.ModerationResponse(
            label=result["label"],
            confidence=result["confidence"],
            categories=result["categories"],
            reason=result["reason"]
        )

    def Chat(self, request, context):
        # 流式响应
        for chunk in agent.stream_chat(request.messages):
            yield agent_service_pb2.ChatResponse(content=chunk)

server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
agent_service_pb2_grpc.add_AgentServiceServicer_to_server(AgentServicer(), server)
server.add_insecure_port('[::]:50051')
server.start()
```

### 2.3 Redis 消息队列（异步长任务）

```python
# Python 端 - 消费者
import redis
import json

r = redis.Redis(host='localhost', port=6379)

def process_moderation_task():
    while True:
        # 阻塞等待消息
        _, task_data = r.brpop("agent:moderation_queue")
        task = json.loads(task_data)
        
        result = moderate_content(task["content"])
        
        # 回写结果
        r.set(f"agent:result:{task['task_id']}", json.dumps(result), ex=3600)

# Python 端 - 发布结果通知
r.publish("agent:moderation_done", json.dumps({"task_id": task["task_id"]}))
```

```java
// Java 端 - 发送异步任务
@Service
public class AgentAsyncService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public String submitModerationTask(String content, Long userId) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> task = Map.of(
            "task_id", taskId,
            "content", content,
            "user_id", userId
        );
        redisTemplate.opsForList().rightPush("agent:moderation_queue",
            objectMapper.writeValueAsString(task));
        return taskId;
    }

    public ModerationResult getTaskResult(String taskId) {
        String result = redisTemplate.opsForValue().get("agent:result:" + taskId);
        if (result == null) return null;
        return objectMapper.readValue(result, ModerationResult.class);
    }
}
```

## 3. 项目结构

```
recloud/
├── src/                          # Java 后端
│   └── main/java/com/recloud/
│       ├── controller/
│       ├── service/
│       │   └── AgentService.java  # 调用 Python Agent
│       ├── entity/
│       └── mapper/
├── agent-service/                 # Python Agent 服务
│   ├── app/
│   │   ├── main.py               # FastAPI 入口
│   │   ├── agent/
│   │   │   ├── graph.py          # LangGraph 定义
│   │   │   ├── tools.py          # 工具函数
│   │   │   └── prompts.py        # Prompt 模板
│   │   ├── rag/
│   │   │   ├── embeddings.py     # Embedding 模型
│   │   │   ├── vector_store.py   # 向量数据库
│   │   │   ├── splitter.py       # 文本分块
│   │   │   └── pipeline.py       # RAG 流水线
│   │   ├── moderation/
│   │   │   ├── rule_engine.py    # 规则引擎
│   │   │   ├── llm_moderator.py  # LLM 审核
│   │   │   └── review_queue.py   # 人工审核队列
│   │   └── api/
│   │       ├── moderate.py       # 审核 API
│   │       ├── search.py         # 搜索 API
│   │       └── chat.py           # 对话 API
│   ├── requirements.txt
│   ├── Dockerfile
│   └── tests/
├── docker-compose.yml             # 编排
├── frontend/                      # Vue 前端
├── novels/                        # 小说文件
└── docs/
    └── agent-reference/           # 参考文档
```

## 4. Docker Compose 编排

```yaml
# docker-compose.yml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: recloud
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  milvus:
    image: milvusdb/milvus:latest
    ports:
      - "19530:19530"

  java-backend:
    build: .
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/recloud
      AGENT_BASE_URL: http://agent-service:8000
    depends_on:
      - mysql
      - agent-service

  agent-service:
    build: ./agent-service
    ports:
      - "8000:8000"
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      MILVUS_HOST: milvus
      REDIS_HOST: redis
    depends_on:
      - milvus
      - redis

  frontend:
    build: ./frontend
    ports:
      - "5173:80"
    depends_on:
      - java-backend

volumes:
  mysql_data:
```

## 5. Python requirements.txt

```txt
# Agent框架
langgraph>=0.2.0
langchain>=0.3.0
langchain-openai>=0.2.0

# Web框架
fastapi>=0.115.0
uvicorn>=0.30.0

# RAG
chromadb>=0.5.0
# milvus-sdk>=2.4.0  # 生产环境用
sentence-transformers>=3.0.0
rank-bm25>=0.2.2

# 评估
ragas>=0.2.0

# 工具
pyahocorasick>=2.1.0
flashtext>=2.7
pydantic>=2.0.0
httpx>=0.27.0

# 通信
redis>=5.0.0
grpcio>=1.65.0
grpcio-tools>=1.65.0
```
