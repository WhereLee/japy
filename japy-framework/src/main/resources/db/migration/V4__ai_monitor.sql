-- ============================================================
-- japy-framework V4：AI 运维分析顾问（Monitor Agent）
-- 表前缀 ai_ 隔离；阈值参数入 sys_config 可动态调整
-- ============================================================

-- 信号（规则层输出的事实）
CREATE TABLE ai_monitor_event (
    id            BIGSERIAL PRIMARY KEY,
    monitor_code  VARCHAR(50)  NOT NULL,        -- 检测器 code
    monitor_name  VARCHAR(100) NOT NULL,        -- 检测器名称
    severity      SMALLINT DEFAULT 1,           -- 1信息 2警告 3严重
    fingerprint   VARCHAR(200),                 -- 去重指纹（code+关键维度）
    summary       VARCHAR(1000),                -- 规则层事实描述
    evidence      TEXT,                         -- 证据数据（JSON 文本，避免 jsonb 类型转换开销）
    status        SMALLINT DEFAULT 0,           -- 0待解读 1已解读 2已确认 3已忽略
    insight       TEXT,                         -- LLM 解读（人话）
    root_cause    TEXT,                         -- 根因推测（LLM）
    suggestion    TEXT,                         -- 建议动作草稿（LLM）
    confidence    DECIMAL(3,2),                 -- 置信度（LLM 自评 0-1）
    confirmed_by  BIGINT,
    confirmed_at  TIMESTAMP,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_event_status ON ai_monitor_event(status);
CREATE INDEX idx_event_code_time ON ai_monitor_event(monitor_code, created_at);
CREATE INDEX idx_event_fingerprint ON ai_monitor_event(fingerprint, created_at);

-- 建议卡（L2 人工审批载体）
CREATE TABLE ai_suggestion (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL,
    action      TEXT NOT NULL,                  -- 建议动作
    impact      VARCHAR(500),                   -- 影响评估
    risk        VARCHAR(500),                   -- 风险
    status      SMALLINT DEFAULT 0,             -- 0待审 1已批准 2已驳回 3已执行
    handled_by  BIGINT,
    handled_at  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sug_status ON ai_suggestion(status);

-- 分析日志（LLM 调用审计：谁/问了什么/消耗多少）
CREATE TABLE ai_analysis_log (
    id               BIGSERIAL PRIMARY KEY,
    biz_type         VARCHAR(30) NOT NULL,      -- interpret/feedback_analysis
    ref_id           BIGINT,                    -- 关联 id（event 等）
    prompt_summary   VARCHAR(500),
    response_summary TEXT,
    model            VARCHAR(50),
    token_in         INT DEFAULT 0,
    token_out        INT DEFAULT 0,
    cost             DECIMAL(10,4) DEFAULT 0,
    cost_time        BIGINT,
    success          SMALLINT DEFAULT 1,        -- 0失败 1成功
    trace_id         VARCHAR(64),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 反馈（人工对信号/建议的评价，自由文本为核心）
CREATE TABLE ai_feedback (
    id          BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,           -- event/suggestion
    target_id   BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    rating      SMALLINT NOT NULL,              -- 1好评 0差评
    reason_tag  VARCHAR(30),                    -- 误报/判断错误/建议不可行/信息有用/已按建议处理
    comment     TEXT,                           -- 自由文本（核心反馈方式）
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_feedback_target ON ai_feedback(target_type, target_id);

-- 反馈洞察（LLM 分析反馈文本后的改进建议，人工确认后应用）
CREATE TABLE ai_feedback_insight (
    id              BIGSERIAL PRIMARY KEY,
    batch_no        VARCHAR(30),
    cluster_result  TEXT,                       -- 聚类结果
    improvement     TEXT,                       -- 改进建议
    status          SMALLINT DEFAULT 0,         -- 0待应用 1已应用 2已忽略
    applied_by      BIGINT,
    applied_at      TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 站内通知（严重信号即时通知；与 moments 的 notification 表不同名，无冲突）
CREATE TABLE sys_notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(200),
    content     TEXT,
    is_read     SMALLINT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notif_user ON sys_notification(user_id, is_read);

-- ============================================================
-- 检测器阈值参数（管理员可在参数管理页面调整）
-- ============================================================
INSERT INTO sys_config (config_name, config_key, config_value, remark) VALUES
('登录爆破-失败次数阈值',   'monitor.loginBruteForce.maxFail',    '5',    '时间窗口内同一IP失败次数'),
('登录爆破-时间窗口(分)',   'monitor.loginBruteForce.windowMin', '5',    '统计窗口'),
('接口错误突增-倍数',       'monitor.apiErrorSurge.ratio',       '3',    '24h计数/7日均值'),
('接口错误突增-最小样本',   'monitor.apiErrorSurge.minSample',   '20',   '低于此样本不检测'),
('慢操作-耗时阈值(ms)',     'monitor.slowOps.thresholdMs',       '5000', '超过视为慢操作'),
('失败率-阈值',             'monitor.apiFailRate.ratio',         '0.03', '接口失败率阈值'),
('失败率-最小样本',         'monitor.apiFailRate.minSample',     '50',   '低于此样本不检测'),
('锁定风暴-倍数',           'monitor.lockStorm.ratio',           '3',    '当日锁定/7日均值'),
('异常时段-开始(时)',       'monitor.abnormalHour.start',        '23',   ''),
('异常时段-结束(时)',       'monitor.abnormalHour.end',          '6',    ''),
('日志表增长-警戒行数',     'monitor.logGrowth.warnRows',        '5000000', '达到该行数报警'),
('误报阈值提示-N条',        'monitor.thresholdHint.count',       '3',    '连续N条误报触发阈值调整提示'),
('AI-模型',                 'ai.llm.model',                      'deepseek-v4-flash', ''),
('AI-BaseUrl',              'ai.llm.baseUrl',                    'https://api.deepseek.com/v1', '');

-- ============================================================
-- 权限：AI 运维模块（admin 自动绑定）
-- ============================================================
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, icon, sort) VALUES
(1000, 1, 'AI 运维', NULL, 2, '/ai/ops', 'Cpu', 10),
(1001, 1000, '监测报告', 'ai:report:list', 3, NULL, NULL, 1),
(1002, 1000, '信号列表', 'ai:event:list', 3, NULL, NULL, 2),
(1003, 1000, '信号确认', 'ai:event:confirm', 3, NULL, NULL, 3),
(1004, 1000, '建议卡处理', 'ai:suggestion:handle', 3, NULL, NULL, 4),
(1005, 1000, '反馈提交', 'ai:feedback:add', 3, NULL, NULL, 5),
(1006, 1000, '反馈分析', 'ai:insight:analyze', 3, NULL, NULL, 6),
(1007, 1000, '手动检测', 'ai:event:run', 3, NULL, NULL, 7);

INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 1000 AND 1007;
