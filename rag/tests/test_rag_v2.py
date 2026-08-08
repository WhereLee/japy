"""RAG 链路行为契约测试 —— 独立测试工程师视角（全新编写，不沿用旧用例）

覆盖契约：
  A. agent.py 静态契约：定位边界 prompt、max_tokens 输出上限、上下文组装
  B. generate_answer 行为契约：流式产出 / API 失败降级 / 历史注入 / 消息组装
  C. rag_api.ask 契约：参数校验、索引校验、空结果、相关性守卫（含省 token 关键契约）
  D. rag_api 其余接口：health / status / sync（单本、全量、锁清理）

测试原则：
  - 不调用真实 LLM（mock OpenAI，省 token、结果可重复）
  - 不依赖运行中的服务（FastAPI TestClient 直接驱动 app）
  - 不依赖真实数据库（mock pg_store 与 retriever，注入受控返回值）
"""
import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

import pytest
from fastapi.testclient import TestClient

import agent as agent_module
import rag_api
from agent import SYSTEM_PROMPT, build_context, generate_answer
from rag_api import app

client = TestClient(app)


# ---------------------------------------------------------------------------
# 工具：可控的 OpenAI 假实现（流式 + 可注入失败）
# ---------------------------------------------------------------------------
class FakeCompletions:
    def __init__(self):
        self.last_kwargs = None
        self.fail_on = None

    def create(self, **kwargs):
        self.last_kwargs = kwargs
        if kwargs.get("model") == self.fail_on:
            raise RuntimeError("simulated LLM API failure")
        # 模拟流式返回：两段内容
        return iter([
            SimpleNamespace(choices=[SimpleNamespace(delta=SimpleNamespace(content="第一段回答。"))]),
            SimpleNamespace(choices=[SimpleNamespace(delta=SimpleNamespace(content="第二段回答。"))]),
        ])


class FakeChat:
    def __init__(self):
        self.completions = FakeCompletions()


class FakeOpenAI:
    def __init__(self, *args, **kwargs):
        self.chat = FakeChat()


# ---------------------------------------------------------------------------
# A. agent.py 静态契约
# ---------------------------------------------------------------------------
class TestAgentStaticContract:
    def test_system_prompt_has_positioning_boundary(self):
        """定位边界：prompt 必须明确只答本书相关、拒绝无关问题（用途越界防御）"""
        assert "只回答与本书内容相关" in SYSTEM_PROMPT
        assert "无关问题" in SYSTEM_PROMPT
        assert "编程技术" in SYSTEM_PROMPT
        assert "只讨论与本书相关的内容" in SYSTEM_PROMPT

    def test_system_prompt_keeps_truthfulness_rules(self):
        """事实底线：不编造、无依据不推测（原有契约不可因改动丢失）"""
        assert "不能捏造事实" in SYSTEM_PROMPT
        assert "这些片段里没有涉及这个内容" in SYSTEM_PROMPT

    def test_max_tokens_is_4000(self, monkeypatch):
        """输出上限：max_tokens 必须为 4000（成本/失控兜底）"""
        fake = FakeOpenAI()
        monkeypatch.setattr(agent_module, "OpenAI", lambda *a, **k: fake)
        list(generate_answer("问题", [{"chunk_id": 1, "content": "片段", "chapter_title": "第一章", "chapter_index": 0}]))
        assert fake.chat.completions.last_kwargs["max_tokens"] == 4000

    def test_build_context_sorts_by_chapter(self):
        """上下文组装：乱序输入必须按故事时间序（chapter_index）排列"""
        chunks = [
            {"chunk_id": 3, "content": "第三章内容", "chapter_title": "第三章", "chapter_index": 2},
            {"chunk_id": 1, "content": "第一章内容", "chapter_title": "第一章", "chapter_index": 0},
            {"chunk_id": 2, "content": "第二章内容", "chapter_title": "第二章", "chapter_index": 1},
        ]
        ctx = build_context(chunks)
        assert ctx.index("第一章内容") < ctx.index("第二章内容") < ctx.index("第三章内容")

    def test_build_context_marks_source(self):
        """上下文组装：每个片段必须带章节标题与 chunk_id（来源可追溯）"""
        ctx = build_context([{"chunk_id": 7, "content": "内容X", "chapter_title": "第五章", "chapter_index": 4}])
        assert "第五章" in ctx
        assert "chunk_7" in ctx


# ---------------------------------------------------------------------------
# B. generate_answer 行为契约
# ---------------------------------------------------------------------------
class TestGenerateAnswerBehavior:
    def _fake_stream(self, monkeypatch):
        fake = FakeOpenAI()
        monkeypatch.setattr(agent_module, "OpenAI", lambda *a, **k: fake)
        return fake

    def test_streams_tokens_and_sources(self, monkeypatch):
        """流式契约：产出 token，最后必须以 __SOURCES__ 输出来源 JSON"""
        fake = self._fake_stream(monkeypatch)
        chunks = [{"chunk_id": 1, "content": "片段内容", "chapter_title": "第一章", "chapter_index": 0, "score": 0.9}]
        tokens = list(generate_answer("问题", chunks))
        assert "第一段回答。" in "".join(tokens)
        assert "第二段回答。" in "".join(tokens)
        sources_line = next(t for t in tokens if t.startswith("\n__SOURCES__"))
        import json
        sources = json.loads(sources_line[len("\n__SOURCES__"):])
        assert sources[0]["chunk_id"] == 1
        assert sources[0]["chapter_title"] == "第一章"
        assert "score" in sources[0]

    def test_degradation_on_api_failure(self, monkeypatch):
        """降级契约：LLM 调用彻底失败 → 返回检索原文片段，不静默空答"""
        fake = FakeOpenAI()
        fake.chat.completions.fail_on = "deepseek-fail-model"
        # 让 create 每次都抛：直接 monkeypatch create 抛异常
        def boom(**kw):
            raise RuntimeError("simulated LLM API failure")
        fake.chat.completions.create = boom
        monkeypatch.setattr(agent_module, "OpenAI", lambda *a, **k: fake)
        chunks = [{"chunk_id": 1, "content": "降级片段", "chapter_title": "第一章", "chapter_index": 0}]
        tokens = list(generate_answer("问题", chunks))
        full = "".join(tokens)
        assert "[LLM 暂不可用]" in full
        assert "降级片段" in full
        assert any(t.startswith("\n__SOURCES__") for t in tokens)

    def test_injects_recent_history(self, monkeypatch):
        """历史注入：最多带最近 20 条消息进 messages"""
        fake = self._fake_stream(monkeypatch)
        history = [{"role": "user" if i % 2 == 0 else "assistant", "content": f"msg{i}"} for i in range(30)]
        list(generate_answer("问题", [{"chunk_id": 1, "content": "片段", "chapter_title": "第一章", "chapter_index": 0}], history=history))
        msgs = fake.chat.completions.last_kwargs["messages"]
        # 1 system + 最近 20 条历史 + 1 user = 22
        assert len(msgs) == 22
        assert msgs[1]["content"] == "msg10"  # 从第 10 条开始（30-20）

    def test_user_message_contains_context_and_question(self, monkeypatch):
        """消息组装：user 消息必须同时包含检索片段与读者问题"""
        fake = self._fake_stream(monkeypatch)
        list(generate_answer("特殊问题ABC", [{"chunk_id": 1, "content": "特殊片段XYZ", "chapter_title": "第一章", "chapter_index": 0}]))
        user_msg = fake.chat.completions.last_kwargs["messages"][-1]["content"]
        assert "特殊片段XYZ" in user_msg
        assert "特殊问题ABC" in user_msg


# ---------------------------------------------------------------------------
# C. rag_api.ask 契约（含相关性守卫）
# ---------------------------------------------------------------------------
class TestAskContract:
    """每个用例注入可控的 retriever（search 返回固定 results/meta）与 pg_store（has_index）"""

    def _install(self, monkeypatch, results=None, meta=None, has_index=True):
        retriever = MagicMock()
        retriever.search.return_value = (results or [], meta or {})
        monkeypatch.setattr(rag_api, "_get_retriever", lambda nid: retriever)
        fake_store = MagicMock()
        fake_store.has_index.return_value = has_index
        monkeypatch.setattr(rag_api, "pg_store", fake_store)
        return retriever

    def _ask(self, **body):
        return client.post("/api/rag/ask", json=body)

    def test_requires_novel_id_and_question(self, monkeypatch):
        """参数契约：缺 novel_id 或 question → 400"""
        self._install(monkeypatch)
        assert self._ask(question="问题").status_code == 400
        assert self._ask(novel_id=1).status_code == 400

    def test_rejects_overlong_question(self, monkeypatch):
        """参数契约：question 超过 500 字 → 400"""
        self._install(monkeypatch)
        r = self._ask(novel_id=1, question="长" * 501)
        assert r.status_code == 400

    def test_conflict_when_no_index(self, monkeypatch):
        """索引契约：索引未构建 → 409（提示先同步）"""
        self._install(monkeypatch, has_index=False)
        r = self._ask(novel_id=1, question="星海征途讲什么？")
        assert r.status_code == 409

    def test_apology_when_no_results(self, monkeypatch):
        """空结果契约：检索无命中 → 道歉语 + 空 sources（不调 LLM）"""
        self._install(monkeypatch, results=[], meta={"top_score": 0})
        with patch.object(rag_api, "generate_answer") as mock_gen:
            r = self._ask(novel_id=1, question="完全无关的内容？")
        assert r.status_code == 200
        assert "没有检索到" in r.json()["data"]["answer"]
        assert r.json()["data"]["sources"] == []
        mock_gen.assert_not_called()

    def test_guard_blocks_unrelated_and_skips_llm(self, monkeypatch):
        """【关键契约】相关性守卫：top_score<=0 时拦截 + 引导语 + 绝不调用 LLM（省 token）"""
        results = [{"id": 1, "content": "碰巧含关键词的段落", "chapter_no": 3, "score": -6.35}]
        self._install(monkeypatch, results=results, meta={"top_score": -6.35, "result_count": 12})
        with patch.object(rag_api, "generate_answer") as mock_gen:
            r = self._ask(novel_id=1, question="如何用 Java 写二分查找？")
        data = r.json()["data"]
        assert r.status_code == 200
        assert data["meta"]["guarded"] is True
        assert "只讨论与本书相关的内容" in data["answer"]
        assert data["sources"] == []
        mock_gen.assert_not_called()  # 省 token 契约：未调用 LLM

    def test_guard_boundary_exactly_zero(self, monkeypatch):
        """边界契约：top_score 恰为 0.0 → 触发守卫（<=0 语义）"""
        results = [{"id": 1, "content": "段落", "chapter_no": 1, "score": 0.0}]
        self._install(monkeypatch, results=results, meta={"top_score": 0.0})
        with patch.object(rag_api, "generate_answer") as mock_gen:
            r = self._ask(novel_id=1, question="边缘问题")
        assert r.json()["data"]["meta"]["guarded"] is True
        mock_gen.assert_not_called()

    def test_guard_allows_related_question(self, monkeypatch):
        """守卫放行契约：top_score>0 → 正常调用 generate_answer + 组装 sources"""
        results = [{"id": 1, "content": "晨星号收到信号", "chapter_no": 3, "score": 2.12}]
        self._install(monkeypatch, results=results, meta={"top_score": 2.12})

        def fake_gen(q, chunks, history=None):
            yield "这是回答。"
            import json
            yield "\n__SOURCES__" + json.dumps([{"chunk_id": c["chunk_id"], "chapter_title": c["chapter_title"]} for c in chunks], ensure_ascii=False)

        with patch.object(rag_api, "generate_answer", fake_gen):
            r = self._ask(novel_id=1, question="晨星号收到了什么信号？")
        data = r.json()["data"]
        assert data["meta"].get("guarded", False) is False
        assert "这是回答。" in data["answer"]
        assert data["sources"][0]["chunk_id"] == 1
        assert data["sources"][0]["chapter_no"] == 3

    def test_sources_format(self, monkeypatch):
        """来源格式契约：chunk_id/chapter_no/content_preview/score 字段齐全"""
        results = [{"id": 9, "content": "很长" * 100, "chapter_no": 5, "score": 1.5}]
        self._install(monkeypatch, results=results, meta={"top_score": 1.5})

        def fake_gen(q, chunks, history=None):
            yield "答。"
            import json
            yield "\n__SOURCES__" + json.dumps([], ensure_ascii=False)

        with patch.object(rag_api, "generate_answer", fake_gen):
            r = self._ask(novel_id=1, question="测试来源格式")
        src = r.json()["data"]["sources"][0]
        assert set(src.keys()) == {"chunk_id", "chapter_no", "content_preview", "score"}
        assert len(src["content_preview"]) <= 120 + 3  # 预览截断 120 字符


# ---------------------------------------------------------------------------
# E. 提示词注册表契约（agent 读取数据库 prompt，保存即生效；无记录回退内置）
# ---------------------------------------------------------------------------
class TestPromptRegistryContract:
    def test_uses_db_prompt_when_available(self, monkeypatch):
        """注册表契约：数据库有生效 prompt 时，system 消息用数据库内容（而非内置）"""
        fake = FakeOpenAI()
        monkeypatch.setattr(agent_module, "OpenAI", lambda *a, **k: fake)
        # 模拟数据库返回自定义提示词
        monkeypatch.setattr(agent_module, "get_active_prompt", lambda code: "【DB版】你是定制提示词。")
        list(generate_answer("问题", [{"chunk_id": 1, "content": "片段", "chapter_title": "第一章", "chapter_index": 0}]))
        msgs = fake.chat.completions.last_kwargs["messages"]
        assert msgs[0]["role"] == "system"
        assert msgs[0]["content"] == "【DB版】你是定制提示词。"

    def test_falls_back_to_builtin_when_no_db_record(self, monkeypatch):
        """注册表契约：数据库无记录时回退内置 SYSTEM_PROMPT（防提示词缺失）"""
        fake = FakeOpenAI()
        monkeypatch.setattr(agent_module, "OpenAI", lambda *a, **k: fake)
        monkeypatch.setattr(agent_module, "get_active_prompt", lambda code: None)
        list(generate_answer("问题", [{"chunk_id": 1, "content": "片段", "chapter_title": "第一章", "chapter_index": 0}]))
        msgs = fake.chat.completions.last_kwargs["messages"]
        assert msgs[0]["content"] == SYSTEM_PROMPT


# ---------------------------------------------------------------------------
# D. rag_api 其余接口契约
# ---------------------------------------------------------------------------
class TestMiscEndpoints:
    def test_health(self):
        """探活契约：health 必须 200 且 ok=True"""
        r = client.get("/api/rag/health")
        assert r.status_code == 200
        assert r.json()["ok"] is True

    def test_status_returns_chunk_count(self, monkeypatch):
        """状态契约：status 返回该书的 chunk 计数（透传 pg_store.chunk_count）"""
        fake_store = MagicMock()
        fake_store.chunk_count.return_value = {"novel_id": 1, "count": 20}
        monkeypatch.setattr(rag_api, "pg_store", fake_store)
        r = client.get("/api/rag/status", params={"novel_id": 1})
        assert r.status_code == 200
        assert r.json()["data"]["count"] == 20

    def test_sync_starts_async_task(self, monkeypatch):
        """同步契约（异步版）：触发立即返回 task_started；后台线程执行 sync_novel"""
        monkeypatch.setattr(rag_api, "sync_novel", lambda nid, progress_cb=None: {"novel_id": nid, "chunks": 10})
        monkeypatch.setattr(rag_api, "sync_all", lambda progress_cb=None: {"synced": []})
        invalidated = []
        monkeypatch.setattr(rag_api, "_invalidate", lambda nid: invalidated.append(nid))
        # 清掉可能残留的任务状态
        rag_api._sync_tasks.pop(2, None)
        r = client.post("/api/rag/sync", json={"novel_id": 2})
        assert r.status_code == 200
        assert r.json()["data"]["task_started"] is True
        assert r.json()["data"]["novel_id"] == 2
        # 轮询等待后台任务完成（异步），完成后缓存必须失效
        assert self._wait_task_done(2), "后台同步任务未在时限内完成"
        assert invalidated == [2], "同步完成后必须使检索器缓存失效"

    def test_sync_all_starts_async_task(self, monkeypatch):
        """同步契约（异步版）：全量同步后台执行，全部书缓存失效"""
        monkeypatch.setattr(rag_api, "sync_all", lambda progress_cb=None: {"synced": [{"novel_id": 1}, {"novel_id": 2}]})
        invalidated = []
        monkeypatch.setattr(rag_api, "_invalidate", lambda nid: invalidated.append(nid))
        rag_api._sync_tasks.pop(0, None)
        r = client.post("/api/rag/sync", json={})
        assert r.status_code == 200
        assert r.json()["data"]["task_started"] is True
        assert self._wait_task_done(0), "全量同步任务未在时限内完成"
        assert sorted(invalidated) == [1, 2]

    def test_sync_task_cleaned_after_success(self, monkeypatch):
        """任务状态契约：同步成功后任务锁清理（防内存泄漏）"""
        monkeypatch.setattr(rag_api, "sync_novel", lambda nid, progress_cb=None: {"novel_id": nid})
        rag_api._sync_tasks.pop(5, None)
        r = client.post("/api/rag/sync", json={"novel_id": 5})
        assert r.status_code == 200
        assert self._wait_task_done(5), "同步任务未完成"
        assert 5 not in rag_api._task_locks, "任务锁必须清理"

    def test_sync_task_cleaned_on_failure(self, monkeypatch):
        """任务状态契约：同步异常时任务状态为 failed、锁清理、全局信号量释放"""
        def boom(nid, progress_cb=None):
            raise RuntimeError("sync failed")
        monkeypatch.setattr(rag_api, "sync_novel", boom)
        rag_api._sync_tasks.pop(6, None)
        r = client.post("/api/rag/sync", json={"novel_id": 6})
        assert r.status_code == 200, "异步触发不因任务失败而 500"
        assert self._wait_task_done(6), "失败任务未结束"
        assert rag_api._sync_tasks[6]["status"] == "failed"
        assert "sync failed" in rag_api._sync_tasks[6]["error"]
        assert 6 not in rag_api._task_locks, "失败后任务锁也必须清理"
        # 全局信号量必须已释放（可立即再次获取）
        assert rag_api._sync_semaphore.acquire(timeout=1), "失败后全局信号量必须释放"
        rag_api._sync_semaphore.release()

    @staticmethod
    def _wait_task_done(novel_id: int, timeout: float = 5.0) -> bool:
        """轮询等待异步任务进入终态（done/failed）"""
        import time
        deadline = time.time() + timeout
        while time.time() < deadline:
            key = novel_id if novel_id else 0
            st = rag_api._sync_tasks.get(key, {}).get("status")
            if st in ("done", "failed"):
                return True
            time.sleep(0.05)
        return False
