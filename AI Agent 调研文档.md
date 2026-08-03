# AI Agent 技术调研文档（备用）

> 调研时间：2026-08（在线抓取，两批资料）
> 批次一（2024 经典框架）：Anthropic《Building Effective Agents》、LangChain《What is an AI agent?》、《State of AI 2024》——**基础架构知识，至今有效**
> 批次二（2025-2026 时新）：Anthropic《How we built our multi-agent research system》(2025-06)、LangChain《State of AI Agents》企业调研(2026-06 发布)
> 用途：为 japy-framework 引入 AI Agent 能力提供决策依据

---

## 1. 核心结论（先说重点）

- **企业 Agent 早已不是 RAG**。RAG 只是"增强 LLM"的一种手段；2024 年起行业重心转向**多步骤、自主决策、工具调用的 Agent 工作流**，2025 年更进一步走向**多 Agent 系统**。
- **越简单越好**（Anthropic 反复强调）：最成功的实现都用简单可组合的模式，复杂度只在确实需要时增加。
- **2025 年数据**：51% 的公司已有 Agent 在生产（中型公司 100-2000 人最激进，达 63%），78% 有计划。

---

## 2. Agent 是什么：两类系统（Anthropic 定义）

| 类别 | 定义 | 特点 |
|---|---|---|
| **Workflow（工作流）** | LLM 与工具通过**预定义代码路径**编排 | 可预测、一致性好，适合定义清晰的任务 |
| **Agent（智能体）** | LLM **动态自主**决定流程与工具使用 | 灵活，适合开放式问题；代价是更高延迟与成本 |

补充（LangChain Harrison Chase 视角）：**Agent 的本质是"LLM 决定应用的流程控制"**。自主程度是一个光谱（agentic spectrum）：

```
Router（路由） → State Machine（状态机） → Autonomous Agent（自主智能体）
   低自主 ←—————————————————————————————→ 高自主
```

自主度越高，越需要编排框架（LangGraph）、后台持久执行、运行中可观测、评估框架。

---

## 3. 五种工作流模式 + 自主 Agent（Anthropic 2024 权威框架）

| 模式 | 机制 | 适用 | 企业实例 |
|---|---|---|---|
| **Prompt chaining（链式）** | 每次 LLM 调用处理上一步输出，可加程序化检查点 | 任务可干净拆分为固定子步骤 | 写文案 → 翻译 → 校验 |
| **Routing（路由）** | 先分类输入，再分发给专门的子流程 | 存在可区分的类别 | 客服分流：简单问题走小模型，难题走强模型（省成本） |
| **Parallelization（并行）** | 同时跑多个 LLM 再聚合；分 Sectioning / Voting 两种 | 子任务独立 / 需要多视角提高置信度 | 护栏：一个模型生成内容，另一个独立筛查违规 |
| **Orchestrator-workers（编排-工人）** | 中央 LLM 动态拆任务、派给 worker、汇总结果 | 子任务无法预知 | 编码产品、Anthropic 多 Agent 研究系统（2025 实战） |
| **Evaluator-optimizer（评估-优化）** | 一个 LLM 生成，另一个评估并反馈，循环迭代 | 有明确评估标准且迭代有价值 | 文学翻译、复杂检索多次迭代 |
| **Autonomous agent（自主智能体）** | LLM 在工具调用循环中自主推进，可设置停止条件（最大轮数） | 开放式问题、步骤不可预知 | SWE-bench 修 issue、Computer Use |

**Anthropic 三原则**：① 保持设计简单；② 透明（显示 agent 的规划步骤）；③ 精心设计工具（ACI，见 §7）。

---

## 4. 2024 数据基础（LangChain《State of AI 2024》）

- **工具调用爆发**：含 tool call 的 trace 从 2023 年 **0.5% → 2024 年 21.9%**（LLM 自主决定调什么工具 = Agent 化标志）。
- **LangGraph 采用**：43% 的组织在向 LangGraph 发送编排 trace（自 2024-03 发布以来稳步增长）。
- **任务更复杂但更省**：平均每任务步骤 2.8 → **7.7 步**，但 LLM 调用仅 1.1 → **1.4 次**（用更少的模型调用做更多的事——工具承担计算）。
- **评估成标配**：LLM-as-Judge（用 LLM 给 LLM 输出打分），测试指标：相关性 / 正确性 / 精确匹配 / 有用性；人工反馈注释量一年增长 18 倍。
- 模型提供商：OpenAI 使用量仍第一（6 倍于第二名 Ollama）；本地/开源（Ollama、Mistral、HuggingFace）合计约占 top20 的 20%。

---

## 5. 2025-2026 时新进展（重点阅读）

### 5.1 企业采用现状（LangChain 2025 调研，1300+ 专业人士）

- **51% 已有 Agent 在生产**；中型公司（100-2000 人）最激进（63%）；**78% 有计划**投产。
- **前三大用例**：
  1. **研究与总结（58%）**——文献综述、资料提炼
  2. **个人效率助手（53.5%）**——日程、整理
  3. **客服（45.8%）**——咨询、排障、加速响应
- **最大障碍是性能质量**（占比是成本/安全的两倍以上）——Agent 用 LLM 控制流程天然不稳定，保证输出质量是投产第一难题。
- **控制手段成为标配**：tracing/可观测（第一）、护栏（guardrails）、**只读工具权限或关键动作人工审批**（大企业更谨慎，倾向只读权限）。多数公司不允许 Agent 自由读写删除。
- **明星产品**：Cursor（编码 Agent，最出圈）、Perplexity（搜索问答）、Replit（自动开发部署）——**Agent 已不是理论，是生产工具**。

### 5.2 多 Agent 系统（Anthropic 2025-06 实战，Claude Research）

Anthropic 把自家 Research 功能（多 Agent 研究系统）从原型到生产的完整经验公开，核心要点：

- **架构**：orchestrator-worker 模式——lead agent 规划研究策略 → 动态创建并行 subagents（各自独立 context）→ 汇总合成 → 引用校验 Agent。
- **效果数据**：多 Agent（Opus 4 领队 + Sonnet 4 工人）在内部研究评测上比单 Agent **高 90.2%**；token 用量解释 BrowseComp 评测 80% 的性能方差。
- **成本现实**：Agent 平均消耗约 **4×** 聊天 token，多 Agent 约 **15×**——经济性要求"任务价值足够高"。
- **Prompt 工程经验**（直接可抄）：
  - 教编排者如何委派：每个 subagent 要有明确目标、输出格式、工具边界（否则子任务重复/遗漏）
  - **按查询复杂度伸缩投入**：简单事实 1 个 agent 3-10 次工具调用；复杂研究才上 10+ subagents
  - 工具设计是成败关键：MCP 生态让工具描述质量参差，坏描述会把 agent 带偏
  - 搜索"先宽后窄"（先泛查询探索，再聚焦）
  - 用扩展思考（extended thinking）作为可控草稿纸
  - **并行工具调用 + 并行 subagents 可提速最多 90%**
- **评估方法**：20 个真实查询起步（小样本立即开始，别等大评测集）；**LLM-as-judge 打分**（事实准确性/引用准确/完整性/来源质量/工具效率，0.0-1.0 + pass/fail）；**人工测试不可替代**（发现自动评测漏掉的幻觉与来源偏好）。
- **生产可靠性**（与常规软件差异最大）：
  - Agent 有状态、错误会复合——需要**持久执行 + 断点续跑**（不能出错就从头来）
  - 部署用**彩虹部署**（新旧版本并行、流量渐进切换）——运行中的 agent 可能在任何步骤
  - 同步编排是瓶颈，异步是下一步方向
  - 子代理产物直接写文件系统（artifact 模式），避免"传话游戏"信息损耗

### 5.3 生态动向（2025）

- **MCP（Model Context Protocol）**：工具标准化协议，2025 年被主流采用，Agent 可插拔接入第三方工具生态（Anthropic 多 Agent 系统中已大量使用 MCP servers）。
- **编码 Agent 爆发**：Claude Code、Cursor、GitHub Copilot Agent、OpenAI Codex 等——"测试闭环 + 可验证"使编码成为 Agent 最高价值场景。
- **Agent 平台化**：低代码平台（Dify/Coze）与企业 Agent 平台（OpenAI Agents SDK、Google A2A 等）相继出现。

---

## 6. 企业两大高价值场景（贯穿 2024-2025 的实证）

1. **客服 Agent**：聊天界面 + 工具集成（查用户数据、订单历史、知识库）+ 可编程动作（退款、更新工单）。成功标准清晰（问题解决率），已有"按成功解决才收费"的商业模式验证。
2. **编码 Agent**：SWE-bench 上仅凭 PR 描述解决真实 GitHub issue；自动化测试提供反馈闭环，仍需人工评审。

---

## 7. 运维/开发者向 Agent（AIOps）形态

对应"开发者/运维端"的 Agent，企业里长这样：

| 能力 | 实现 | 可复用的现有数据 |
|---|---|---|
| **日志智能问答** | "为什么昨天 3 点接口超时？"→ 工具查日志 → 归纳回答 | oper_log / 登录日志 |
| **异常自动排查** | 周期扫描：爆破尝试、高频失败 → 生成报告/通知 | 登录锁定记录 |
| **根因分析** | 多步编排（orchestrator-workers）：日志 → 指标 → 配置 → 结论 | actuator 指标 + 配置 |
| **可观测** | 每个 agent 步骤可回放、可评估 | traceId 已具备 |

---

## 8. 工具设计（ACI，Anthropic 附录精华）

- 工具定义/文档的投入要像"给 junior 开发写 docstring"一样认真。
- 给出示例用法、边界条件、与其他工具的区分。
- 防错（Poka-yoke）：把参数设计得难以用错（如必须绝对路径）。
- 实测：SWE-bench 编码 agent 中，优化工具的时间比优化提示词还多；Anthropic 甚至用"工具测试 agent"自动改写工具描述，使任务完成时间降低 40%。

---

## 9. AI 技术栈全景（词汇表，供学习）

**模型层**
- LLM：GPT / Claude / Gemini / DeepSeek / Qwen（通义）/ Llama
- Embedding 模型：文本向量化（RAG 的基石），如 BGE / text-embedding-3
- 部署方式：API（云）/ 本地推理（Ollama、vLLM）

**增强层**
- RAG（检索增强生成）：切块 → 向量化 → 检索 → 生成
- Function Calling / Tool Use：LLM 输出结构化工具调用指令（Agent 的核心动作）
- Memory（记忆）：短期（会话内）/ 长期（向量库持久化）
- Prompt 工程：系统提示词、few-shot、结构化输出

**编排层（框架）**
- LangChain（Python/JS）：链式组合
- **LangGraph**：有状态图编排，支持分支、循环、持久执行、人工中断（行业主流）
- CrewAI：多角色协作；AutoGen（微软）：多 Agent 对话
- Spring AI（Java）/ Semantic Kernel（微软，Java/.NET）：Java 生态接入
- Dify / Coze（扣子）/ 阿里百炼：低代码 Agent 平台

**协议层**
- **MCP（Model Context Protocol）**：2024 年底 Anthropic 推出的工具标准化协议，2025 年被广泛采用（"AI 世界的 USB-C"）

**可观测与评估层**
- LangSmith / Langfuse：trace 回放、调试、成本监控
- Promptfoo：提示词回归测试
- LLM-as-Judge：用 LLM 对输出打分（相关性/正确性/有用性）

**基础设施层**
- 向量库：pgvector（PostgreSQL 插件）/ FAISS / Chroma / Milvus / Qdrant / Elasticsearch
- 流式传输：SSE / WebSocket
- LLM 网关：统一代理、密钥管理、限流、成本计量

**安全层**
- Prompt Injection 防护、输出过滤/脱敏、密钥管理（环境变量/密钥服务）

---

## 10. 与 japy-framework 的结合点与落地路线

**结合点**：认证/RBAC/审计/日志/字典/限流/traceId 已就绪，天然是 Agent 的工具库与安全底座。

**推荐路线**（遵循"从简单开始"原则）：
1. **第一步（最小闭环）**：Java 手写"增强 LLM + 工具调用循环"，2-3 个运维工具（查操作日志/查登录日志/查用户），`/ai/chat` SSE 流式接口，复用 Security/@RateLimit/@OperLog；新增 `ai_call_log` 审计表
2. **第二步**：复杂度上来（多步编排/分支/人工确认）→ 引入 langgraph；工具层不变
3. **第三步**：业务数据就绪后接 RAG（pgvector 零新增中间件）；再往后按需演进多 Agent（参考 §5.2 的 orchestrator-worker 模式与教训）

**必须遵守的企业级要求**（2025 调研印证）：密钥环境变量化、AI 调用全审计、限流降级、**工具只读权限 + 关键动作人工审批**（大企业共识）、tracing 可观测、LLM-as-judge 评估、按查询复杂度伸缩 token 预算。

---

## 11. 参考链接

**2024 经典**
- Anthropic《Building Effective Agents》(2024-12-19)：https://www.anthropic.com/engineering/building-effective-agents
- LangChain《What is an AI agent?》(Harrison Chase, 2024-06-28)：https://www.langchain.com/blog/what-is-an-agent
- LangChain《State of AI 2024 Report》：https://blog.langchain.com/state-of-ai-agents/

**2025-2026 时新**
- Anthropic《How we built our multi-agent research system》(2025-06-13)：https://www.anthropic.com/engineering/built-multi-agent-research-system
- LangChain《State of AI Agents》企业调研（1300+ 人）：https://www.langchain.com/stateofaiagents
- MCP 协议官网：https://modelcontextprotocol.io/
