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
    private com.japy.module.ai.monitor.LockStormMonitor lockStormMonitor;
    @Autowired
    private com.japy.module.ai.monitor.ApiErrorSurgeMonitor apiErrorSurgeMonitor;
    @Autowired
    private com.japy.module.ai.monitor.ApiFailRateMonitor apiFailRateMonitor;
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

    @Test
    @Order(2)
    void 锁定风暴检测() {
        // 过去 7 天每天 1 条锁定（日均 1），今天 6 条 → 6 > 1*3 命中
        for (int d = 1; d <= 7; d++) {
            SysLoginLog lock = lockLogin("10.8.0." + d);
            lock.setLoginTime(java.time.LocalDateTime.now().minusDays(d));
            loginLogMapper.insert(lock);
        }
        for (int i = 0; i < 6; i++) {
            loginLogMapper.insert(lockLogin("10.8.0." + i));
        }
        var events = lockStormMonitor.check();
        assertFalse(events.isEmpty(), "今日锁定 6 次应命中锁定风暴");
    }

    @Test
    @Order(2)
    void 接口错误突增检测() {
        // 近 24h 失败 30 条；近 7 天（3 天前）失败 10 条（日均 1.4）→ 30 > 1.4*3 且 >= 20 命中
        for (int i = 0; i < 30; i++) {
            operLogMapper.insert(failOper("/system/user"));
        }
        for (int i = 0; i < 10; i++) {
            SysOperLog old = failOper("/system/user");
            old.setOperTime(java.time.LocalDateTime.now().minusDays(3));
            operLogMapper.insert(old);
        }
        var events = apiErrorSurgeMonitor.check();
        assertTrue(events.stream().anyMatch(e -> e.getEvidence().get("url").equals("/system/user")),
                "24h 失败 30 次应命中错误突增");
    }

    @Test
    @Order(2)
    void 接口失败率检测() {
        // 50 条中 5 条失败 → 10% > 3% 且样本 >= 50 命中
        for (int i = 0; i < 45; i++) {
            SysOperLog ok = new SysOperLog();
            ok.setOperUrl("/system/user");
            ok.setRequestMethod("GET");
            ok.setStatus(0);
            operLogMapper.insert(ok);
        }
        for (int i = 0; i < 5; i++) {
            operLogMapper.insert(failOper("/system/user"));
        }
        var events = apiFailRateMonitor.check();
        assertTrue(events.stream().anyMatch(e -> e.getEvidence().get("url").equals("/system/user")),
                "失败率 10% 应命中失败率检测");
    }

    @Test
    @Order(3)
    void AI接口权限校验() throws Exception {
        // 普通用户访问 /ai/** → 403
        JsonNode reg = postJson("/auth/register",
                Map.of("username", "t_ai_perm_" + nextTs(), "password", "123456", "nickname", "普通用户"), null);
        String userToken = reg.get("data").get("accessToken").asText();
        JsonNode denied = getJson("/ai/report", userToken);
        assertEquals(403, denied.get("code").asInt(), denied.toString());
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

        // 已批准的建议卡可标记执行 → 状态 3
        JsonNode approved = getJson("/ai/suggestions?page=1&size=1&status=1", admin);
        if (approved.get("data").get("records").size() > 0) {
            long id3 = approved.get("data").get("records").get(0).get("id").asLong();
            assertEquals(200, postJson("/ai/suggestions/" + id3 + "/execute", Map.of(), admin).get("code").asInt());
        }
        JsonNode executed = getJson("/ai/suggestions?page=1&size=10&status=3", admin);
        assertEquals(200, executed.get("code").asInt());
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

    /** 锁定记录（msg 含"锁定"） */
    private SysLoginLog lockLogin(String ip) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername("t_lock_" + nextTs());
        log.setIpaddr(ip);
        log.setStatus(1);
        log.setMsg("密码错误次数过多，账号已锁定");
        return log;
    }

    /** 失败操作日志 */
    private SysOperLog failOper(String url) {
        SysOperLog log = new SysOperLog();
        log.setOperUrl(url);
        log.setRequestMethod("GET");
        log.setStatus(1);
        log.setErrorMsg("测试失败");
        return log;
    }
}
