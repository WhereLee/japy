# 并发与多用户架构设计

> 项目：japy（小说平台：japy-framework 后端 + japy-admin 管理端 + rag Python 检索服务）
> 文档目的：记录多用户并发场景下的全部设计决策——**哪些做了、为什么做、哪些刻意不做、为什么不做、何时才该做**。
> 面试视角：每个条目都是可讲的"并发问题 + 我的解法 + 取舍理由"。

---

## 一、并发场景总览

| 场景 | 角色 | 核心路径 | 已做优化 |
|---|---|---|---|
| 多用户同时问书 | 普通用户 | 提问 → 检索 → 精排 → LLM 生成 | 连接池 / rerank 排队 / LLM 限流 |
| 多管理员同时操作 | 业务管理员 | 上传 / 同步 / 审核 | 审核幂等 / @Async 线程池 |
| 多实例一致性 | （上线前） | 任务状态 / 互斥 / 缓存 | 状态迁 Redis / 分布式锁 |

---

## 二、已实施的优化（6 项）

### 1. 数据库连接池（rag / pg_store）

**问题**：原 `_connect()` 每次新建 psycopg2 连接，一次问答占 3~4 个连接（has_index → vector_search → get_active_prompt），并发峰值 = 请求数 × 3，无上限。

**方案**：`ThreadedConnectionPool(min=5, max=20)` + `Semaphore(20)` 限流（acquire 5s 超时明确报"连接池繁忙"）。
- 池内复用，并发从"无上限新建"变"20 封顶"
- `with _connect() as conn:` 调用点语义不变（自动 commit/rollback/归还）

**验证**：10 并发查询全部成功；PG 每连接 ~10MB，20 条远低于 max_connections=100。

### 2. Rerank 排队友好化（rag / retriever_pg）

**问题**：bge-reranker 单实例 569MB，`Semaphore(1)` 全局串行 + 15s 超时——原实现超时**静默降级 RRF**（用户无感知拿到变差结果）。

**方案**：
- 保留信号量（推理安全闸门）
- 新增**等待队列上限** `RERANK_QUEUE_MAX=8`：队列满 → 抛 `RerankBusyError` → 上层返回 **429 + "问答高峰，请稍后再试"**（不静默降级）
- acquire 超时同样转 429（不再返回降级结果）

**取舍**：batch rerank（合并多用户请求一次推理，吞吐 ×4）列为二期——需要压测数据证明 rerank 是瓶颈才上，避免过度设计。

### 3. LLM 并发与重试（rag / agent）

**问题**：多用户同时调 DeepSeek，无并发控制（并发 429 概率上升）；重试固定 3s 非指数。

**方案**：
- **并发信号量** `LLM_MAX_CONCURRENT=8`：超出排队（最多 30s），超时降级返回原文片段
- **指数退避 + 抖动**：1s → 2s（×2 指数 + 0.5s 随机抖动），覆盖 429/超时，MAX_RETRIES 1→2
- 信号量在 `finally` 释放（异常不泄漏）

### 4. 审核幂等（japy-framework / AuditService）

**问题**：`handle()` 无事务，"读-判-写"（select → check PENDING → update）是 TOCTOU 非原子——两个管理员同时处理同一条审核会**都通过**（重复处理）。

**方案**：`@Transactional` + **条件更新**：
```sql
UPDATE novel_audit SET result=?, auditor_id=?, ...
WHERE id=? AND result='PENDING'   -- 影响 0 行 = 已被他人处理
```
读-判-写原子化，无需悲观锁。影响 0 行 → 明确提示"该记录已被处理（请刷新列表）"。

**验证**：`AuditConcurrencyTest`——两个线程并发处理同一条，恰好一人成功。

### 5. @Async 线程池（japy-framework / AsyncConfig）

**问题**：`@EnableAsync` 无自定义 Executor 时用 `SimpleAsyncTaskExecutor`——每次任务新建线程、**无上限**（并发上传触发 RAG 同步会线程无界）。

**方案**：`ThreadPoolTaskExecutor`：core=2 / max=4 / queue=100 / **CallerRuns 背压**（队列满由提交线程执行，不静默丢弃）/ 线程名前缀 `japy-async-`。

### 6. 任务状态迁 Redis + 分布式锁（rag / rag_api）

**问题**：同步任务状态 `_sync_tasks` 内存 dict + 进程内 `Semaphore(1)`——**进程重启丢失、多实例各说各话**。

**方案**：
- **状态**：Redis Hash `rag:sync:task`（novel_id → 状态 JSON），TTL 10 分钟自动清理（替代 Timer）
- **互斥**：Redis 分布式锁 `SET rag:sync:lock NX EX 300`（TTL 防死锁），跨进程/跨实例一致
- 进度回调每批写 Redis（同步是低频批处理，开销可接受）

**验证**：`redis-cli hget rag:sync:task 1` 直接可见任务状态（跨进程）；完成后锁可立即获取。

---

## 三、刻意不做（YAGNI / 成本收益决策）

### 7. 上传全异步化

**现状**：上传解析（切章/统计/落盘/扫描）在请求线程同步完成，实测 **45ms**。

**不做理由**：为 45ms 的操作引入完整异步状态机（任务表/状态接口/前端轮询），与 RAG 同步那套重复，复杂度翻倍，投入产出比极低。

**何时该做**：单本上传 >10s（千万字级），或要"批量拖入 50 本后台排队"——那时 RAG 同步的异步范式**直接套用**（现状已经示范了这套模式）。

**面试话术**："我们知道异步化怎么做（RAG 索引同步就是异步任务 + 进度轮询的完整实现），但上传 45ms 不值得为它上状态机——这是判断力，不是不会。"

### 8. FastAPI 线程池调大 / uvicorn 多 worker

**不做理由（两个独立原因）**：
1. **调大线程池是治标**：瓶颈在下游资源（rerank 1 个、LLM 并发、PG 连接），不在线程数。40 线程默认值在资源瓶颈修完后根本打不满。
2. **多 worker 是负优化**（调研确认）：每个 worker 进程**各自加载一份** embedder(~100MB) + reranker(569MB)，内存 ×N；且 rerank 信号量变每进程独立（全局互斥失效）。除非模型**进程外服务化**（TEI/dynamic batching），否则多 worker 是拿内存换吞吐。

**何时该做**：压测真实打满 40 + 有监控数据证明线程是瓶颈；上多 worker 前先上模型独立服务（TEI）。

**面试话术**："多数人会回答'加 worker 提高并发'，我调研过：模型进程内加载时多 worker 会内存翻倍、信号量失效，正确解法是模型服务化——知道为什么不能无脑加机器，比会加更值钱。"

---

## 四、测试与验证基线

| 层 | 结果 |
|---|---|
| Java（含 AuditConcurrencyTest 并发幂等） | 61/61 |
| Python（含 Redis 任务状态/分布式锁契约） | 30/30 |
| 真实并发实测 | 3 并发问书全部 200（12.6-13.7s）；Redis 状态跨进程可见；双管理员同审核恰好一人成功 |

## 五、上线前待办（触发条件触发时再做）

1. 多实例部署：Java 侧 AiPromptService/字典缓存迁 Redis 失效广播（当前单实例一致）
2. 模型服务化（TEI）后允许多 worker/多实例
3. 上传批量异步化（产品形态变化时）
