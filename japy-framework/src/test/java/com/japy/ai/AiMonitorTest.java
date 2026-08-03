package com.japy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.japy.base.AbstractIntegrationTest;
import com.japy.module.ai.entity.AiFeedbackInsight;
import com.japy.module.ai.entity.AiMonitorEvent;
import com.japy.module.ai.llm.LlmClient;
import com.japy.module.ai.llm.LlmResponse;
import com.japy.module.ai.mapper.AiMonitorEventMapper;
import com.japy.module.ai.monitor.LoginBruteForceMonitor;
import com.japy.module.ai.monitor.MonitorEvent;
import com.japy.module.ai.monitor.MonitorScheduler;
import com.japy.module.ai.monitor.SlowOpsMonitor;
import com.japy.module.ai.service.AiMonitorService;
import com.japy.module.system.entity.SysLoginLog;
import com.japy.module.system.entity.SysOperLog;
import com.japy.module.system.mapper.SysLoginLogMapper;
import com.japy.module.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AI 运维分析顾问集成测试。
 * 注意：@Transactional 使每个用例独立回滚，因此用例必须"自给自足"（各自造数据）。
 * LLM 层使用 Mock（stub），不调用真实 API（测试稳定且零成本）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class AiMonitorTest extends AbstractIntegrationTest {

    @Autowired
    private SysLoginLogMapper loginLogMapper;
    @Autowired
    private SysOperLogMapper operLogMapper;
    @Autowired
    private LoginBruteForceMonitor bruteForceMonitor;
    @Autowired
    private SlowOpsMonitor slowOpsMonitor;
    @Autowired
    private AiMonitorService monitorService;
    @Autowired
    private AiMonitorEventMapper eventMapper;

    @MockBean
    private LlmClient llmClient;
    /** 禁用调度器（fixedDelay 启动即执行一次），避免测试期间后台任务干扰 */
    @MockBean
    private MonitorScheduler monitorScheduler;

    private String uniqueIp;

    @BeforeEach
    void setUp() {
        uniqueIp = "10.9." + (Long.parseLong(nextTs()) % 200) + "." + (Long.parseLong(nextTs()) % 250);
        when(llmClient.available()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(new LlmResponse(
                "{\"insight\":\"测试解读：检测到异常\",\"rootCause\":\"测试原因\",\"suggestion\":\"测试建议：检查并处理\",\"confidence\":0.8}",
                10, 20));
    }

    // ---------- 检测器单测（不落库，只验证规则） ----------

    @Test
    @Order(1)
    void 登录爆破检测命中与边界() {
        // 命中：同一 IP 失败 5 次（默认阈值）
        for (int i = 0; i < 5; i++) {
            loginLogMapper.insert(failLogin(uniqueIp));
        }
        List<MonitorEvent> hit = bruteForceMonitor.check();
        assertTrue(hit.stream().anyMatch(e -> uniqueIp.equals(e.getEvidence().get("ip"))),
                "失败 5 次的 IP 应命中爆破检测");
        hit.stream().filter(e -> uniqueIp.equals(e.getEvidence().get("ip")))
                .forEach(e -> assertEquals(3, e.getSeverity(), "爆破应为严重级别"));

        // 边界：另一 IP 仅 4 次，不命中
        String otherIp = "10.9.200.200";
        for (int i = 0; i < 4; i++) {
            loginLogMapper.insert(failLogin(otherIp));
        }
        List<MonitorEvent> miss = bruteForceMonitor.check();
        assertTrue(miss.stream().noneMatch(e -> otherIp.equals(e.getEvidence().get("ip"))),
                "4 次失败不应命中爆破检测");
    }

    @Test
    @Order(2)
    void 慢操作检测() {
        SysOperLog slow = new SysOperLog();
        slow.setTitle("用户管理");
        slow.setMethod("POST /system/user");
        slow.setOperUrl("/system/user");
        slow.setRequestMethod("POST");
        slow.setStatus(0);
        slow.setCostTime(8000L); // 超过默认 5000ms 阈值
        operLogMapper.insert(slow);
        assertFalse(slowOpsMonitor.check().isEmpty(), "耗时 8s 应命中慢操作检测");
    }

    // ---------- 全链路（落库） ----------

    @Test
    @Order(3)
    void 手动检测全链路信号解读建议卡通知() throws Exception {
        prepareBruteForce();
        String admin = loginAsAdmin();

        JsonNode run = postJson("/ai/events/run", Map.of(), admin);
        assertEquals(200, run.get("code").asInt(), run.toString());

        // 信号落库且已被 LLM 解读（stub）
        AiMonitorEvent e = findBruteForceEvent();
        assertNotNull(e, "爆破信号应落库");
        assertEquals(1, e.getStatus(), "信号应已被 LLM 解读");
        assertEquals("测试建议：检查并处理", e.getSuggestion());

        // 严重信号 → 站内通知
        JsonNode notifs = getJson("/ai/notifications?page=1&size=10", admin);
        assertEquals(200, notifs.get("code").asInt());
        assertTrue(notifs.get("data").get("total").asInt() >= 1, "严重信号应有站内通知");

        // 严重度>=2 → 建议卡（待审）
        JsonNode sugs = getJson("/ai/suggestions?page=1&size=20&status=0", admin);
        assertEquals(200, sugs.get("code").asInt());
        assertTrue(sugs.get("data").get("total").asInt() >= 1, "应有待审建议卡");
    }

    @Test
    @Order(4)
    void 同指纹去重() throws Exception {
        prepareBruteForce();
        String admin = loginAsAdmin();
        postJson("/ai/events/run", Map.of(), admin);
        // 立即再跑一轮：同指纹 30 分钟窗口内应被去重
        JsonNode second = postJson("/ai/events/run", Map.of(), admin);
        assertEquals(200, second.get("code").asInt(), second.toString());
        // 断言：本用例 IP 的爆破指纹事件只有 1 条（按 IP 过滤，避免其他测试/检测器干扰）
        long cnt = eventMapper.selectList(null).stream()
                .filter(x -> ("login_brute_force:" + uniqueIp).equals(x.getFingerprint()))
                .count();
        assertEquals(1, cnt, "同指纹 30 分钟内不应重复产生信号");
    }

    // ---------- 建议卡审批流 ----------

    @Test
    @Order(5)
    void 建议卡审批流() throws Exception {
        prepareBruteForce();
        String admin = loginAsAdmin();
        postJson("/ai/events/run", Map.of(), admin);

        // 待审建议卡 → 批准
        JsonNode pending = getJson("/ai/suggestions?page=1&size=1&status=0", admin);
        assertTrue(pending.get("data").get("records").size() > 0, "应有待审建议卡");
        long sugId = pending.get("data").get("records").get(0).get("id").asLong();
        assertEquals(200, postJson("/ai/suggestions/" + sugId + "/approve", Map.of(), admin).get("code").asInt());

        // 再驳回一张
        JsonNode pending2 = getJson("/ai/suggestions?page=1&size=1&status=0", admin);
        if (pending2.get("data").get("records").size() > 0) {
            long id2 = pending2.get("data").get("records").get(0).get("id").asLong();
            assertEquals(200, postJson("/ai/suggestions/" + id2 + "/reject", Map.of(), admin).get("code").asInt());
        }
        JsonNode rejected = getJson("/ai/suggestions?page=1&size=10&status=2", admin);
        assertEquals(200, rejected.get("code").asInt());
    }

    // ---------- 反馈闭环 ----------

    @Test
    @Order(6)
    void 反馈提交与阈值提示() throws Exception {
        prepareBruteForce();
        String admin = loginAsAdmin();
        postJson("/ai/events/run", Map.of(), admin);
        AiMonitorEvent e = findBruteForceEvent();

        // 提交差评（误报标签 + 自由文本）
        JsonNode fb = postJson("/ai/feedback", Map.of(
                "targetType", "event",
                "targetId", e.getId(),
                "rating", 0,
                "reasonTag", "误报",
                "comment", "这是我们内部测试账号的登录，不是爆破"), admin);
        assertEquals(200, fb.get("code").asInt(), fb.toString());

        // 阈值提示接口正常
        JsonNode hint = getJson("/ai/feedback/hint?monitorCode=login_brute_force", admin);
        assertEquals(200, hint.get("code").asInt());
        assertNotNull(hint.get("data"), hint.toString());
    }

    @Test
    @Order(7)
    void 反馈分析生成洞察() throws Exception {
        prepareBruteForce();
        String admin = loginAsAdmin();
        postJson("/ai/events/run", Map.of(), admin);
        AiMonitorEvent e = findBruteForceEvent();
        // 先造两条带自由文本的反馈
        postJson("/ai/feedback", Map.of("targetType", "event", "targetId", e.getId(),
                "rating", 0, "reasonTag", "误报", "comment", "内部测试账号误报，应排除测试 IP 段"), admin);
        postJson("/ai/feedback", Map.of("targetType", "event", "targetId", e.getId(),
                "rating", 1, "reasonTag", "信息有用", "comment", "这个建议很具体，已按建议处理"), admin);

        JsonNode analyze = postJson("/ai/insight/analyze", Map.of(), admin);
        assertEquals(200, analyze.get("code").asInt(), analyze.toString());
        AiFeedbackInsight insight = om.convertValue(analyze.get("data"), AiFeedbackInsight.class);
        assertEquals(0, insight.getStatus(), "洞察初始应为待应用状态");
        assertNotNull(insight.getClusterResult(), "聚类结果不应为空");
    }

    // ---------- 报告 ----------

    @Test
    @Order(8)
    void 报告生成() throws Exception {
        String admin = loginAsAdmin();
        JsonNode report = getJson("/ai/report", admin);
        assertEquals(200, report.get("code").asInt(), report.toString());
        JsonNode data = report.get("data");
        assertTrue(data.get("eventTotal").asInt() >= 0);
        assertTrue(data.get("llmAvailable").asBoolean(), "stub LLM 应可用");
        assertNotNull(data.get("events"));
    }

    // ---------- 工具 ----------

    /** 插入 5 条同 IP 失败登录（触发爆破检测的数据前提） */
    private void prepareBruteForce() {
        for (int i = 0; i < 5; i++) {
            loginLogMapper.insert(failLogin(uniqueIp));
        }
    }

    /** 查找本用例创建的爆破信号 */
    private AiMonitorEvent findBruteForceEvent() {
        return eventMapper.selectList(null).stream()
                .filter(x -> "login_brute_force".equals(x.getMonitorCode())
                        && uniqueIp.equals(x.getFingerprint().split(":")[1]))
                .findFirst().orElse(null);
    }

    private SysLoginLog failLogin(String ip) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername("t_ai_" + nextTs());
        log.setIpaddr(ip);
        log.setStatus(1);
        log.setMsg("密码错误");
        return log;
    }
}
