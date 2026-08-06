-- ============================================================
-- V13: 内容治理模块（管理端上传 → 规则扫描 → 留痕）
-- 流程：管理端上传小说 → AC 规则引擎扫描（毫秒级）→ 写 audit 记录
--   - 无命中 → PASS（合规留痕）
--   - 有命中 → PENDING（小说保持上架，管理端"内容审核"页提示 → 人工确认通过/下架）
-- 边界：audit 域独立，只读 novel 的 id，不修改 novel 字段（下架由 controller 编排调用 novelService）
-- ============================================================

-- 1. 敏感词库（管理端可维护：新增/停用）
CREATE TABLE jf_sensitive_word (
    id          BIGSERIAL PRIMARY KEY,
    word        VARCHAR(50) NOT NULL,
    category    VARCHAR(20) DEFAULT '其他',   -- 政治/色情/暴力/广告/其他
    level       SMALLINT DEFAULT 2,            -- 1高危 2低危
    status      SMALLINT DEFAULT 0,            -- 0启用 1停用
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_word UNIQUE (word)
);
COMMENT ON TABLE jf_sensitive_word IS '敏感词库';

-- 2. 审核记录（每次扫描一条）
CREATE TABLE jf_novel_audit (
    id          BIGSERIAL PRIMARY KEY,
    novel_id    BIGINT NOT NULL,
    audit_type  VARCHAR(20) DEFAULT 'UPLOAD',  -- UPLOAD上传 / RESCAN重扫
    rule_hits   TEXT,                          -- JSON: [{"word":"xxx","category":"色情","count":3}]
    result      VARCHAR(20) DEFAULT 'PENDING', -- PENDING待审 PASS通过 REJECT驳回 TAKEDOWN下架
    auditor_id  BIGINT,                        -- 处理人（admin）
    audit_time  TIMESTAMP,
    remark      VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_jf_audit_novel ON jf_novel_audit (novel_id);
CREATE INDEX idx_jf_audit_result ON jf_novel_audit (result);
COMMENT ON TABLE jf_novel_audit IS '小说审核记录';

-- 3. seed 敏感词（示例词库，管理端可增删）
INSERT INTO jf_sensitive_word (word, category, level) VALUES
('禁赌','政治',1),
('赌博','政治',1),
('博彩','政治',1),
('成人内容','色情',1),
('裸聊','色情',1),
('约炮','色情',1),
('杀人教程','暴力',1),
('血腥屠杀','暴力',1),
('代开发票','广告',1),
('加微信领红包','广告',2),
('点击领取','广告',2)
ON CONFLICT (word) DO NOTHING;

-- 4. 管理端菜单：内容审核（挂"小说管理"目录下）+ 按钮权限
-- admin 走 *:*:* 通配自动可见；tech_admin 不可见
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1205, 1201, '内容审核', 'audit:list', 2, 'audit', 'novel/audit/index', 'Stamp', 2
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1205);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1206, 1205, '审核处理', 'audit:handle', 3, NULL, NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1206);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1207, 1205, '重新扫描', 'audit:rescan', 3, NULL, NULL, NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1207);
