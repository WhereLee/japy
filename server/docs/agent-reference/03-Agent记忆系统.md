# Agent 记忆系统

## 1. 三层记忆架构

```
┌─────────────────────────────────────────────┐
│            工作记忆（Working Memory）          │
│  当前对话窗口的最近 N 轮消息                    │
│  存储位置：内存 / prompt messages              │
│  生命周期：单次会话                            │
├─────────────────────────────────────────────┤
│           情景记忆（Episodic Memory）          │
│  历史对话的向量化存储                           │
│  存储位置：向量数据库                          │
│  生命周期：长期，可检索                        │
├─────────────────────────────────────────────┤
│           语义记忆（Semantic Memory）          │
│  结构化的用户画像、知识、处理记录               │
│  存储位置：关系数据库                          │
│  生命周期：永久                                │
└─────────────────────────────────────────────┘
```

## 2. 短期记忆（工作记忆）

### 2.1 对话窗口管理策略

```python
from dataclasses import dataclass, field
from typing import Literal

@dataclass
class Message:
    role: Literal["user", "assistant", "system"]
    content: str
    token_count: int = 0

class ConversationMemory:
    """对话窗口管理"""
    def __init__(self, max_tokens: int = 4000, system_prompt: str = ""):
        self.max_tokens = max_tokens
        self.messages: list[Message] = []
        if system_prompt:
            self.messages.append(Message(role="system", content=system_prompt, token_count=len(system_prompt) // 2))

    def add(self, role: str, content: str):
        msg = Message(role=role, content=content, token_count=len(content) // 2)
        self.messages.append(msg)
        self._trim()

    def _trim(self):
        """截断策略：保留系统消息 + 最近N轮"""
        total = sum(m.token_count for m in self.messages)
        while total > self.max_tokens and len(self.messages) > 2:
            # 保留第一条系统消息，删除最早的非系统消息
            for i, m in enumerate(self.messages):
                if m.role != "system":
                    total -= m.token_count
                    self.messages.pop(i)
                    break

    def get_messages(self) -> list[dict]:
        return [{"role": m.role, "content": m.content} for m in self.messages]
```

### 2.2 Token 截断策略

| 策略 | 适用场景 | 说明 |
|------|---------|------|
| 滑动窗口 | 通用 | 保留最近N轮，丢弃早期 |
| 摘要压缩 | 长对话 | 早期对话用LLM压缩为摘要 |
| 重要性保留 | 关键信息 | 根据重要性评分选择性保留 |
| 分层截断 | 复杂场景 | 系统消息必保留 + 最近N轮 + 摘要 |

## 3. 长期记忆（情景记忆）

### 3.1 对话历史向量化存储

```python
from datetime import datetime

class EpisodicMemory:
    """情景记忆：历史对话的向量化存储"""
    def __init__(self, embedding_model, vector_store):
        self.embedding_model = embedding_model
        self.vector_store = vector_store

    async def store_conversation(self, session_id: str, user_id: str,
                                  question: str, answer: str):
        """存储一次对话"""
        content = f"用户问：{question}\n助手答：{answer}"
        embedding = await self.embedding_model.aembed_query(content)
        
        self.vector_store.add(
            ids=[f"{session_id}_{datetime.now().timestamp()}"],
            embeddings=[embedding],
            documents=[content],
            metadatas=[{
                "user_id": user_id,
                "session_id": session_id,
                "timestamp": datetime.now().isoformat(),
                "question": question,
            }]
        )

    async def recall(self, query: str, user_id: str, top_k: int = 5):
        """检索相关历史对话"""
        embedding = await self.embedding_model.aembed_query(query)
        results = self.vector_store.query(
            query_embeddings=[embedding],
            n_results=top_k,
            where={"user_id": user_id}
        )
        return results
```

### 3.2 参考论文

- **Oracle Agent Memory**（2026.7）：数据库原生记忆基底，93.8%准确率，token减少10.7倍
- **Mandol**（2026.6）：聚合式记忆系统，统一向量+图+KV存储，检索速度提升5.4倍
- **MOSS**（2026.7）：可审计的Agent记忆架构，使用结构化关系数据库替代传统RAG

## 4. 语义记忆（结构化存储）

### 4.1 用户画像

```sql
CREATE TABLE user_profile (
    user_id BIGINT PRIMARY KEY,
    total_annotations INT DEFAULT 0,
    total_comments INT DEFAULT 0,
    annotation_types JSON,          -- {"普通": 15, "数据校验": 3}
    favorite_novels JSON,            -- [1, 3, 5]
    avg_annotation_length FLOAT,
    violation_count INT DEFAULT 0,
    last_active_at DATETIME,
    reputation_score FLOAT DEFAULT 0.5,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 4.2 管理记录

```sql
CREATE TABLE admin_action_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,  -- ban_user / reject_annotation / resolve_report
    target_type VARCHAR(50),            -- user / annotation / comment
    target_id BIGINT,
    reason TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin (admin_id),
    INDEX idx_target (target_type, target_id)
);
```

## 5. LangGraph 中的记忆实现

```python
from langgraph.store.memory import InMemoryStore
from langgraph.checkpoint.memory import MemorySaver

# 短期记忆（检查点）
checkpointer = MemorySaver()

# 长期记忆（Store）
store = InMemoryStore()

# 使用
graph = workflow.compile(checkpointer=checkpointer, store=store)

# 运行（带线程ID，支持跨会话）
config = {"configurable": {"thread_id": "user_123"}}
result = graph.invoke(input_data, config)

# 存储长期记忆
store.put(("user_profiles",), "user_123", {
    "name": "李若松",
    "preferences": ["龙族", "天龙八部"],
    "interaction_count": 15
})

# 检索长期记忆
profile = store.get(("user_profiles",), "user_123")
```
