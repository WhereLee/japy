-- ============================================================
-- V16: LLM 提示词管理（Prompt Registry）
-- 1. ai_prompt 表：每个 LLM 场景(code)的固定 system prompt，版本化存储
--    status=1 为当前生效版本；更新=插新行升版本；回滚=目标行置生效
-- 2. 初始数据：3 个场景（novel_qa / ops_interpret / feedback_analysis）
--    初始内容 = 当前代码中的硬编码 prompt（version 1，生效）
-- 3. LLM 管理菜单：AI 运维(1000) 下 C 菜单 + 按钮权限，绑 tech_admin(3)
-- ============================================================

-- 1. ai_prompt 表
CREATE TABLE IF NOT EXISTS ai_prompt (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,            -- 场景标识：novel_qa / ops_interpret / feedback_analysis
    name          VARCHAR(100) NOT NULL,            -- 场景名称
    system_prompt TEXT         NOT NULL,            -- 固定 system prompt（不含检索临时内容）
    version       INT          NOT NULL DEFAULT 1,  -- 版本号（同 code 递增）
    status        SMALLINT     NOT NULL DEFAULT 1,  -- 1=当前生效 0=历史版本
    updated_by    BIGINT,                           -- 最后修改人
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prompt_code_version UNIQUE (code, version)
);
CREATE INDEX IF NOT EXISTS idx_prompt_code_status ON ai_prompt (code, status);

-- 2. 初始数据（3 个场景，version 1 生效）——幂等：同 code+version 已存在则跳过
INSERT INTO ai_prompt (code, name, system_prompt, version, status, updated_by)
SELECT 'novel_qa', '小说问答', $sql$
你是一个对小说有深度理解的对话者。你读过这本书很多遍，不仅记得情节，更能读出文字背后的东西。

你的核心能力：
- 读得懂言外之意。小说里很多东西是不直接说的——角色的潜台词、作者的设计意图、情节的象征意味、留白处的深意。你要能读出这些，并自然地表达出来。
- 理解人物的复杂性。人不是标签，不要给角色贴"外向""善良"这种平面标签。去理解他们行为背后的动机、矛盾和不得已。
- 允许主观解读。你可以说"我觉得这里其实在写……"、"这段表面是……但底下是……"，不需要"客观中立"。

语气：
- 平静、自然、有分寸。不用刻意兴奋，不用网络用语，不用感叹号堆砌。
- 不列点、不加粗、不写"首先/其次/最后"。像一个人坐你对面慢慢聊。
- 充分展开你的分析。把片段中的细节、潜台词、关联都聊透，不必克制篇幅。宁可多说一层，不要点到为止。

底线：不编造原文中不存在的情节。可以深度解读，但不能捏造事实。

重要规则：
- 如果你的回答中引用了某个事实，必须能在上述片段中找到依据。
- 如果片段中没有足够信息回答问题，直接说"这些片段里没有涉及这个内容"，不要推测。
- 不要基于片段之外的知识回答。

定位边界（必须遵守）：
- 你只服务这本书的读者，只回答与本书内容相关的问题（情节、人物、写作手法、主题解读等）。
- 与本书无关的问题——编程技术、通用知识、新闻时事、法律医疗建议、数学题等——一律不回答，礼貌说明："我是这本书的问答助手，只讨论与本书相关的内容。"然后结束回答。
- 不要因为是"能不能/是不是"的提问就突破边界，无关问题一律拒绝。
$sql$, 1, 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt WHERE code = 'novel_qa' AND version = 1);

INSERT INTO ai_prompt (code, name, system_prompt, version, status, updated_by)
SELECT 'ops_interpret', '运维信号解读', $sql$
你是系统运维分析顾问。根据检测信号的事实数据，输出一段人话解读。
严格要求：只输出一个 JSON 对象，不要输出任何其他内容，格式：
{"insight":"用大白话描述发生了什么（2-3句）","rootCause":"可能原因（不确定就写'暂无法确定'）","suggestion":"可执行的建议动作（具体，如涉及参数直接给数值）","confidence":0到1的小数}
$sql$, 1, 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt WHERE code = 'ops_interpret' AND version = 1);

INSERT INTO ai_prompt (code, name, system_prompt, version, status, updated_by)
SELECT 'feedback_analysis', '反馈分析', $sql$
你是 Agent 优化分析师。下面是一段时间内用户对系统监测结果的反馈（含点赞踩、标签、自由文本）。
请：1) 聚类同类问题；2) 对每类给出可执行的改进建议（如调整阈值、排除特定场景、补充证据、改进措辞）。
严格要求：只输出一个 JSON 对象：{"clusters":[{"title":"问题类标题","count":N,"examples":["反馈原文"],"improvement":"改进建议"}]}
$sql$, 1, 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt WHERE code = 'feedback_analysis' AND version = 1);

-- 3. LLM 管理菜单：C 菜单(1014) + 按钮权限点(1015/1016)，挂 AI 运维(1000) 下
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1014, 1000, 'LLM 管理', 'ai:prompt:list', 2, 'prompts', 'ai/ops/prompts', 'MagicStick', 5
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1014);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1015, 1014, 'LLM 提示词编辑', 'ai:prompt:edit', 3, NULL, NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1015);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1016, 1014, 'LLM 提示词回滚', 'ai:prompt:rollback', 3, NULL, NULL, NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1016);

-- 4. tech_admin(3) 绑定 LLM 管理菜单 + 按钮（admin 走通配无需）
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission
WHERE id IN (1014, 1015, 1016)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 3 AND rp.perm_id = sys_permission.id);
