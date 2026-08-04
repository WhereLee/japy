-- ============================================================
-- V7: AI 运维目录补 C 级菜单（若依 M→C→F 三级结构）
-- 问题：V4 只建了 AI 运维目录(1000, M) + 按钮权限点(1001-1007, F)，
--       没有 C 级菜单，前端路由无页面可挂。
-- 本迁移：补 4 个 C 菜单并挂到目录下，按钮权限点改挂对应 C 菜单。
-- 幂等：WHERE NOT EXISTS。
-- ============================================================

-- 1. 补 C 级菜单（component 为前端 views 相对路径，path 为相对路径自动拼父级）
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1010, 1000, '监测报告', 'ai:report:list', 2, 'report', 'ai/ops/report', 'DataLine', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1010);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1011, 1000, '信号列表', 'ai:event:list', 2, 'events', 'ai/ops/events', 'Warning', 2
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1011);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1012, 1000, '建议卡', 'ai:suggestion:handle', 2, 'suggestions', 'ai/ops/suggestions', 'Tickets', 3
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1012);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1013, 1000, '反馈分析', 'ai:feedback:add', 2, 'feedback', 'ai/ops/feedback', 'ChatLineSquare', 4
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1013);

-- 2. 按钮权限点改挂到对应 C 菜单下（保持 M→C→F 三级）
UPDATE sys_permission SET parent_id = 1010 WHERE id = 1001;  -- 监测报告：查看
UPDATE sys_permission SET parent_id = 1011 WHERE id = 1002;  -- 信号列表：查看
UPDATE sys_permission SET parent_id = 1011 WHERE id = 1003;  -- 信号列表：确认/忽略
UPDATE sys_permission SET parent_id = 1011 WHERE id = 1007;  -- 信号列表：手动检测
UPDATE sys_permission SET parent_id = 1012 WHERE id = 1004;  -- 建议卡：处理
UPDATE sys_permission SET parent_id = 1013 WHERE id = 1005;  -- 反馈分析：提交反馈
UPDATE sys_permission SET parent_id = 1013 WHERE id = 1006;  -- 反馈分析：洞察分析

-- 3. 新 C 菜单绑定给 tech_admin（role 3）；admin 走 *:*:* 通配无需绑定
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission WHERE id IN (1010, 1011, 1012, 1013)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 3 AND rp.perm_id = sys_permission.id);
