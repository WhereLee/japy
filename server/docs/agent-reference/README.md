# Agent 技术参考文档

为 ReCloud 项目的 AI Agent 功能扩展准备的技术参考资料。

## 文档索引

| 文件 | 内容 | 关键技术点 |
|------|------|-----------|
| [01-分层审核架构.md](01-分层审核架构.md) | 规则引擎 + LLM分类 + 人工审核 | ahocorasick、频率异常检测、Few-shot Prompt、置信度阈值、人工审核队列 |
| [02-RAG流水线技术.md](02-RAG流水线技术.md) | 分块→嵌入→检索→重排→生成 | RecursiveCharacterTextSplitter、BGE-M3、Milvus/Chroma、HyDE、Rerank、RAGAS |
| [03-Agent记忆系统.md](03-Agent记忆系统.md) | 工作记忆/情景记忆/语义记忆 | 对话窗口管理、向量化历史存储、用户画像、LangGraph Store |
| [04-FunctionCalling工具设计.md](04-FunctionCalling工具设计.md) | 工具定义、路由、错误处理 | OpenAI Function Calling、工具描述技巧、Few-shot、重试机制 |
| [05-Java+Python混合架构.md](05-Java+Python混合架构.md) | 通信方案、项目结构、部署 | REST/gRPC/Redis、FastAPI、Docker Compose、项目目录组织 |
| [06-混合检索技术.md](06-混合检索技术.md) | 向量+BM25分数融合 | rank-bm25、RRF、加权线性组合、性能对比数据 |
| [07-LangGraph与CrewAI.md](07-LangGraph与CrewAI.md) | Agent框架对比和完整示例 | StateGraph、Human-in-the-loop、检查点、选择建议 |

## 面试技术亮点清单

| 亮点 | 对应文档 | 对应能力 |
|------|---------|---------|
| 分层审核架构 | 01 | 工程设计、安全意识 |
| RAG 流水线 | 02 | RAG 落地能力 |
| 三层记忆架构 | 03 | Agent 记忆系统设计 |
| Function Calling | 04 | Agent 编排能力 |
| RAGAS 评估 | 02 | 评估方法论 |
| Java + Python 混合架构 | 05 | 系统架构能力 |
| 向量+关键词混合检索 | 06 | 检索优化能力 |
| LangGraph 状态图 | 07 | Agent 架构设计 |
