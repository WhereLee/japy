# LangGraph & CrewAI 核心概念

## 1. LangGraph

### 1.1 核心组件

| 组件 | 说明 |
|------|------|
| **StateGraph** | 有向图，定义 Agent 工作流 |
| **Node** | 执行操作的函数节点 |
| **Edge** | 连接节点的边 |
| **Conditional Edge** | 根据状态动态路由的条件边 |
| **State** | 所有节点共享的状态对象 |
| **Checkpointer** | 状态持久化（支持中断/恢复） |
| **Store** | 跨线程的长期记忆存储 |

### 1.2 基本用法

```python
from typing import TypedDict, Literal
from langgraph.graph import StateGraph, START, END

# 定义状态
class AgentState(TypedDict):
    messages: list[dict]
    current_step: str
    result: str

# 定义节点
def analyze(state: AgentState) -> dict:
    """分析用户意图"""
    last_msg = state["messages"][-1]["content"]
    # 调用LLM分析意图
    intent = "query"  # 简化
    return {"current_step": intent}

def handle_query(state: AgentState) -> dict:
    """处理查询"""
    return {"result": "查询结果"}

def handle_report(state: AgentState) -> dict:
    """生成报告"""
    return {"result": "报告内容"}

# 条件路由
def route_by_intent(state: AgentState) -> Literal["handle_query", "handle_report"]:
    if state["current_step"] == "query":
        return "handle_query"
    return "handle_report"

# 构建图
graph = StateGraph(AgentState)
graph.add_node("analyze", analyze)
graph.add_node("handle_query", handle_query)
graph.add_node("handle_report", handle_report)

graph.add_edge(START, "analyze")
graph.add_conditional_edges("analyze", route_by_intent)
graph.add_edge("handle_query", END)
graph.add_edge("handle_report", END)

# 编译
app = graph.compile()

# 运行
result = app.invoke({"messages": [{"role": "user", "content": "龙族有多少批注？"}]})
```

### 1.3 Human-in-the-Loop

```python
from langgraph.types import interrupt, Command
from langgraph.checkpoint.memory import MemorySaver

def human_review(state: AgentState) -> Command[Literal["approve", "reject"]]:
    """人工审核节点"""
    # interrupt() 暂停执行，等待人类输入
    human_decision = interrupt({
        "content": state.get("pending_content"),
        "action": "请审核此内容"
    })
    
    if human_decision.get("approved"):
        return Command(goto="approve", update={"result": "已通过"})
    return Command(goto="reject", update={"result": "已拒绝"})

# 编译（需要checkpointer）
app = graph.compile(checkpointer=MemorySaver())

# 首次运行（会在human_review处暂停）
config = {"configurable": {"thread_id": "task_001"}}
result = app.invoke(input_data, config)

# 恢复执行（提供人类决策）
from langgraph.types import Command
result = app.invoke(Command(resume={"approved": True}), config)
```

### 1.4 检查点和持久化

```python
from langgraph.checkpoint.memory import MemorySaver
from langgraph.store.memory import InMemoryStore

# 短期记忆（单线程内）
checkpointer = MemorySaver()

# 长期记忆（跨线程）
store = InMemoryStore()

app = graph.compile(checkpointer=checkpointer, store=store)

# 存储长期记忆
store.put(("user_profiles",), "user_123", {
    "name": "李若松",
    "preferences": ["龙族", "天龙八部"]
})

# 读取长期记忆
profile = store.get(("user_profiles",), "user_123")
```

### 1.5 工具节点

```python
from langchain_core.tools import tool

@tool
def query_database(sql: str) -> str:
    """执行SQL查询"""
    # 执行查询
    return "查询结果"

@tool
def search_vector_db(query: str, top_k: int = 5) -> str:
    """向量检索"""
    return "检索结果"

# 将工具绑定到图
from langgraph.prebuilt import ToolNode

tool_node = ToolNode([query_database, search_vector_db])

graph.add_node("tools", tool_node)
graph.add_conditional_edges("agent", should_use_tools)
graph.add_edge("tools", "agent")
```

---

## 2. CrewAI

### 2.1 核心组件

| 组件 | 说明 |
|------|------|
| **Agent** | 具有角色、目标、工具的AI代理 |
| **Task** | Agent 需要完成的具体任务 |
| **Crew** | 协调多个 Agent 的编排器 |
| **Process** | 执行策略（sequential / hierarchical） |

### 2.2 基本用法

```python
from crewai import Agent, Task, Crew, Process

# 定义 Agent
moderator = Agent(
    role="内容审核员",
    goal="准确识别违规内容，保护社区环境",
    backstory="你是一个经验丰富的社区管理专家，擅长识别各类违规内容。",
    tools=[rule_engine_tool, llm_classify_tool],
    memory=True,
    verbose=True
)

analyst = Agent(
    role="数据分析师",
    goal="分析社区数据，生成有价值的报告",
    backstory="你是一个数据专家，擅长从数据中发现模式和趋势。",
    tools=[query_tool, report_tool],
    memory=True,
    verbose=True
)

# 定义 Task
moderate_task = Task(
    description="审核最新的10条批注，识别违规内容",
    expected_output="审核报告，包含每条批注的审核结果和理由",
    agent=moderator
)

report_task = Task(
    description="基于审核结果，生成本周的社区数据报告",
    expected_output="包含审核统计、违规趋势、用户行为分析的报告",
    agent=analyst
)

# 创建 Crew
crew = Crew(
    agents=[moderator, analyst],
    tasks=[moderate_task, report_task],
    process=Process.sequential,  # 顺序执行
    memory=True,
    verbose=True
)

# 执行
result = crew.kickoff()
```

### 2.3 层级执行模式

```python
crew = Crew(
    agents=[moderator, analyst],
    tasks=[moderate_task, report_task],
    process=Process.hierarchical,  # 管理者自动分配任务
    manager_llm="gpt-4o",  # 管理者使用的模型
    memory=True
)
```

---

## 3. LangGraph vs CrewAI 对比

### 3.1 核心区别

| 维度 | LangGraph | CrewAI |
|------|-----------|--------|
| **抽象级别** | 低级（节点-边-状态） | 高级（角色-任务-团队） |
| **状态管理** | 显式状态图 + 检查点 | 内置记忆系统 |
| **灵活性** | 极高（完全自定义） | 较高（预定义模式） |
| **学习曲线** | 较高 | 较低 |
| **Human-in-the-loop** | 节点级 interrupt() | 任务级触发 |
| **持久化** | 需配置 Checkpointer | 内置 |
| **适用场景** | 复杂状态工作流 | 多Agent协作 |

### 3.2 选择建议（基于2026年论文数据）

根据2026年7月的实证研究（200个新闻任务实验）：

| 架构 | 速度 | 准确率 | 适用场景 |
|------|------|--------|---------|
| 链式设计（LangChain） | ★★★★★ | ★★★ | 速度优先 |
| 多Agent（CrewAI） | ★★★ | ★★★★★（84.7%） | 准确率优先 |
| 单体LLM | ★★★★ | ★★★★ | 通用场景 |
| 迭代架构（AutoGPT） | ★★ | ★★★★ | 可审计性优先 |

### 3.3 你的项目推荐

**推荐用 LangGraph**，原因：

1. 内容审核是**串行流程**（规则→LLM→人工），链式设计更自然
2. 需要**状态持久化**（审核任务中断/恢复）
3. 需要**Human-in-the-loop**（人工审核节点）
4. 管理端对话助手需要**条件路由**（不同意图走不同工具链）
5. 面试时可以讲**状态图设计**，比"用了CrewAI"更有深度

---

## 4. LangGraph 完整示例：内容审核 Agent

```python
from typing import TypedDict, Literal, Annotated
from langgraph.graph import StateGraph, START, END
from langgraph.types import interrupt, Command
from langgraph.checkpoint.memory import MemorySaver

# 状态定义
class ModerationState(TypedDict):
    content: str
    user_id: int
    rule_result: dict | None
    llm_result: dict | None
    final_verdict: str | None
    messages: Annotated[list, "对话历史"]

# 节点定义
def rule_check(state: ModerationState) -> dict:
    """规则引擎检查"""
    result = rule_engine.evaluate(state["content"])
    return {"rule_result": {
        "verdict": result.verdict.value,
        "reason": result.reason,
        "confidence": result.confidence
    }}

def route_after_rules(state: ModerationState) -> Literal["pass", "llm_check", "reject"]:
    rule = state["rule_result"]
    if rule["verdict"] == "reject":
        return "reject"
    if rule["verdict"] == "pass" and rule["confidence"] > 0.9:
        return "pass"
    return "llm_check"

def llm_check(state: ModerationState) -> dict:
    """LLM分类"""
    result = llm_moderator.moderate(state["content"])
    return {"llm_result": result}

def route_after_llm(state: ModerationState) -> Literal["pass", "reject", "human_review"]:
    llm = state["llm_result"]
    if llm["label"] == "safe" and llm["confidence"] >= 0.9:
        return "pass"
    if llm["label"] == "unsafe" and llm["confidence"] >= 0.8:
        return "reject"
    return "human_review"

def human_review(state: ModerationState) -> Command:
    """人工审核"""
    decision = interrupt({
        "content": state["content"],
        "rule_result": state["rule_result"],
        "llm_result": state["llm_result"],
        "action": "请审核此内容"
    })
    if decision.get("approved"):
        return Command(goto="pass", update={"final_verdict": "approved_by_human"})
    return Command(goto="reject", update={"final_verdict": "rejected_by_human"})

def mark_pass(state: ModerationState) -> dict:
    return {"final_verdict": "pass"}

def mark_reject(state: ModerationState) -> dict:
    return {"final_verdict": "reject"}

# 构建图
graph = StateGraph(ModerationState)

graph.add_node("rule_check", rule_check)
graph.add_node("llm_check", llm_check)
graph.add_node("human_review", human_review)
graph.add_node("pass", mark_pass)
graph.add_node("reject", mark_reject)

graph.add_edge(START, "rule_check")
graph.add_conditional_edges("rule_check", route_after_rules)
graph.add_conditional_edges("llm_check", route_after_llm)
graph.add_edge("pass", END)
graph.add_edge("reject", END)

# 编译
app = graph.compile(checkpointer=MemorySaver())

# 使用
config = {"configurable": {"thread_id": "mod_001"}}
result = app.invoke({
    "content": "这段文字包含一些敏感内容...",
    "user_id": 42,
    "rule_result": None,
    "llm_result": None,
    "final_verdict": None,
    "messages": []
}, config)
```
