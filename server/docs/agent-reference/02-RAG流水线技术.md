# RAG 流水线技术详解

## 1. 分块策略（Chunking）

### 1.1 常见策略对比

| 策略 | 适用场景 | chunk_size | overlap | Python库 |
|------|---------|-----------|---------|---------|
| 固定长度切分 | 通用文本 | 500-1000字符 | 50-100字符 | `langchain.text_splitter.RecursiveCharacterTextSplitter` |
| 按段落切分 | 结构化文本 | 按段落边界 | 无 | 自定义splitter |
| 递归字符切分 | 长文档 | 1000字符 | 200字符 | `RecursiveCharacterTextSplitter` |
| 语义切分 | 高质量需求 | 语义边界 | 无 | `semantic-splitter` / `langchain_experimental` |

### 1.2 RecursiveCharacterTextSplitter

```python
from langchain.text_splitter import RecursiveCharacterTextSplitter

splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,
    chunk_overlap=50,
    separators=["\n\n", "\n", "。", "！", "？", ".", "!", "?", " "],
    length_function=len,
)

# 小说正文按段落切分
chunks = splitter.split_text(novel_content)
```

### 1.3 小说场景推荐配置

```python
# 小说正文
novel_splitter = RecursiveCharacterTextSplitter(
    chunk_size=300,     # 小说段落通常100-300字
    chunk_overlap=50,   # 保证上下文连贯
    separators=["\n\n", "\n", "。", "！", "？"],
)

# 批注：按单条切分，不需要splitter
# 每条批注就是一个chunk

# 讨论评论：按单条切分
# 每条评论就是一个chunk
```

## 2. Embedding 模型选型

### 2.1 模型对比

| 模型 | 维度 | 中文效果 | 部署方式 | 成本 |
|------|------|---------|---------|------|
| OpenAI text-embedding-3-small | 1536 | ★★★★ | API | $0.02/1M tokens |
| OpenAI text-embedding-3-large | 3072 | ★★★★★ | API | $0.13/1M tokens |
| BGE-M3（BAAI） | 1024 | ★★★★★ | 本地部署 | 免费 |
| GTE-Qwen2（阿里） | 1536 | ★★★★★ | 本地部署 | 免费 |
| Jina Embeddings v3 | 1024 | ★★★★ | API/本地 | 免费/付费 |

### 2.2 BGE-M3 使用示例

```python
# pip install FlagEmbedding
from FlagEmbedding import BGEM3FlagModel

model = BGEM3FlagModel('BAAI/bge-m3', use_fp16=True)

# 编码
sentences = ["楚子航看着他的背影", "路明非正在发呆"]
embeddings = model.encode(sentences, batch_size=12, max_length=8192)['dense_vecs']
# embeddings.shape = (2, 1024)

# 相似度计算
import numpy as np
similarity = np.dot(embeddings[0], embeddings[1]) / (
    np.linalg.norm(embeddings[0]) * np.linalg.norm(embeddings[1])
)
```

### 2.3 OpenAI Embedding 使用示例

```python
from openai import OpenAI
client = OpenAI()

response = client.embeddings.create(
    model="text-embedding-3-small",
    input="楚子航看着他的背影，忽然想也许自己能捎他一程。"
)
embedding = response.data[0].embedding  # 1536维
```

## 3. 向量数据库选型

### 3.1 对比

| 数据库 | 语言 | 部署方式 | 混合查询 | 适用规模 |
|--------|------|---------|---------|---------|
| Milvus | Go/C++ | Docker/K8s | ✅ 标量+向量 | 大规模 |
| Qdrant | Rust | Docker/云 | ✅ | 中大规模 |
| ChromaDB | Python | 嵌入式/服务 | ❌ | 原型/小规模 |
| PGVector | C | PostgreSQL扩展 | ✅ SQL+向量 | 中规模 |
| FAISS | C++ | 纯库 | ❌ | 单机大规模 |

### 3.2 ChromaDB（原型验证）

```python
# pip install chromadb
import chromadb

client = chromadb.Client()  # 内存模式
# client = chromadb.PersistentClient(path="./chroma_db")  # 持久化

collection = client.create_collection(
    name="novel_chunks",
    metadata={"hnsw:space": "cosine"}
)

# 添加文档
collection.add(
    ids=["chunk_1", "chunk_2"],
    documents=["段落内容1", "段落内容2"],
    metadatas=[
        {"novel_id": 1, "chapter_id": 2, "offset": 0},
        {"novel_id": 1, "chapter_id": 2, "offset": 300},
    ]
)

# 查询
results = collection.query(
    query_texts=["楚子航的性格特点"],
    n_results=5,
    where={"novel_id": 1}  # 标量过滤
)
```

### 3.3 Milvus（生产环境）

```python
# pip install pymilvus
from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType

connections.connect("default", host="localhost", port="19530")

# 定义schema
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1024),
    FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
    FieldSchema(name="novel_id", dtype=DataType.INT64),
    FieldSchema(name="chapter_id", dtype=DataType.INT64),
]
schema = CollectionSchema(fields)
collection = Collection("novel_chunks", schema)

# 创建索引
index_params = {"index_type": "IVF_FLAT", "metric_type": "COSINE", "params": {"nlist": 128}}
collection.create_index("embedding", index_params)

# 插入
collection.insert([embeddings, contents, novel_ids, chapter_ids])

# 搜索（带标量过滤）
collection.load()
results = collection.search(
    data=[query_embedding],
    anns_field="embedding",
    param={"metric_type": "COSINE", "params": {"nprobe": 16}},
    limit=5,
    expr="novel_id == 1",  # 标量过滤
    output_fields=["content", "chapter_id"]
)
```

## 4. 检索优化

### 4.1 HyDE（Hypothetical Document Embeddings）

```python
async def hyde_search(query: str, llm, embedding_model, vector_store, top_k=5):
    """让LLM先生成假想答案，用假想答案做向量检索"""
    # Step 1: 生成假想文档
    hypothetical = await llm.ainvoke(
        f"请根据以下问题，写一段可能的回答（100字左右）：\n{query}"
    )
    
    # Step 2: 用假想文档做向量检索
    hyde_embedding = embedding_model.embed_query(hypothetical.content)
    results = vector_store.similarity_search_by_vector(hyde_embedding, k=top_k)
    
    return results
```

### 4.2 多查询扩展

```python
async def multi_query_search(query: str, llm, vector_store, top_k=5):
    """生成多个不同表述的查询，分别检索后合并去重"""
    # 生成多个查询变体
    prompt = f"""请将以下问题改写为3个不同的表述方式，每行一个：
    原问题：{query}"""
    
    response = await llm.ainvoke(prompt)
    queries = [query] + [q.strip() for q in response.content.split("\n") if q.strip()]
    
    # 分别检索
    all_results = []
    for q in queries:
        results = vector_store.similarity_search(q, k=top_k)
        all_results.extend(results)
    
    # 去重（按文档ID）
    seen = set()
    unique_results = []
    for r in all_results:
        if r.metadata.get("id") not in seen:
            seen.add(r.metadata.get("id"))
            unique_results.append(r)
    
    return unique_results[:top_k]
```

### 4.3 重排序（Rerank）

```python
# pip install sentence-transformers
from sentence_transformers import CrossEncoder

reranker = CrossEncoder('BAAI/bge-reranker-v2-m3', max_length=512)

def rerank(query: str, documents: list[str], top_k=5):
    """用Cross-Encoder精排"""
    pairs = [[query, doc] for doc in documents]
    scores = reranker.predict(pairs)
    
    # 按分数排序
    scored_docs = sorted(zip(scores, documents), reverse=True)
    return [doc for _, doc in scored_docs[:top_k]]
```

### 4.4 完整 RAG 流水线

```python
class RAGPipeline:
    def __init__(self, embedding_model, vector_store, llm, reranker=None):
        self.embedding_model = embedding_model
        self.vector_store = vector_store
        self.llm = llm
        self.reranker = reranker

    async def query(self, question: str, top_k=5, use_hyde=False) -> dict:
        # Step 1: 查询优化
        search_query = question
        if use_hyde:
            search_query = await self._hyde(question)
        
        # Step 2: 向量检索
        candidates = self.vector_store.similarity_search(search_query, k=top_k * 4)
        
        # Step 3: 重排序
        if self.reranker:
            pairs = [[question, doc.page_content] for doc in candidates]
            scores = self.reranker.predict(pairs)
            candidates = [doc for _, doc in sorted(zip(scores, candidates), reverse=True)]
        
        top_docs = candidates[:top_k]
        
        # Step 4: 构造Prompt
        context = "\n\n".join([doc.page_content for doc in top_docs])
        prompt = f"""基于以下上下文回答问题。如果上下文中没有相关信息，请说明。

上下文：
{context}

问题：{question}
回答："""
        
        # Step 5: LLM生成
        response = await self.llm.ainvoke(prompt)
        
        return {
            "answer": response.content,
            "sources": [doc.metadata for doc in top_docs],
            "context": context
        }
```

## 5. RAGAS 评估框架

### 5.1 核心指标

| 指标 | 定义 | 公式 |
|------|------|------|
| **Faithfulness** | 响应与检索上下文的事实一致性 | 支持的声明数 / 总声明数 |
| **Answer Relevancy** | 响应与用户输入的相关程度 | avg(cosine_sim(生成问题, 原始问题)) |
| **Context Precision** | 检索器将相关块排在前面的能力 | Σ(Precision@k × relevance_k) / 总相关数 |
| **Context Recall** | 成功检索了多少相关文档 | 检索支持的参考声明数 / 参考总声明数 |

### 5.2 使用示例

```python
# pip install ragas
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision, context_recall
from datasets import Dataset

# 准备评估数据
eval_data = Dataset.from_dict({
    "question": ["楚子航和路明非是什么关系？"],
    "answer": ["楚子航是路明非的同学和战友"],
    "contexts": [["楚子航看着他的背影，忽然想也许自己能捎他一程。"]],
    "reference": ["楚子航是路明非的同学，两人在卡塞尔学院相识"]
})

result = evaluate(
    eval_data,
    metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
)
print(result)
```

### 5.3 自定义评估数据集构建

```python
# 手动构建评估集
test_cases = [
    {
        "question": "龙族第二章中楚子航做了什么？",
        "ground_truth": "楚子航看着路明非的背影，想捎他一程",
        "relevant_chunks": ["chunk_id_123", "chunk_id_124"]
    },
    # ... 更多测试用例
]

# 目标：50-100条标注好的query-document对
```
