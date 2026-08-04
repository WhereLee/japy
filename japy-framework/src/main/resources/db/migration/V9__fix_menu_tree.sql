-- ============================================================
-- V9: 修复菜单树层级（技术管理员可见性）
-- 问题：
--   1) V4 把 AI 运维(1000) 建为 perm_type=2(菜单)，应为 1(目录)——
--      导致 buildRouterTree 当叶子处理，子菜单(1010-1013)被丢弃
--   2) 操作日志(700)/登录日志(800)/在线用户(900) 挂在"系统管理"目录下，
--      tech_admin 无系统管理目录权限 → 技术管理员看不到日志/在线用户
-- 修复：AI 运维改目录；新建顶级目录"运行监控"承载日志类菜单。
-- ============================================================

-- 1. AI 运维改为目录
UPDATE sys_permission SET perm_type = 1 WHERE id = 1000;

-- 2. 新建顶级目录"运行监控"（parent_id=0）
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1100, 0, '运行监控', NULL, 1, '/monitor', NULL, 'Monitor', 2
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1100);

-- 3. 日志类菜单移入运行监控，path 改为相对路径（自动拼父目录）
UPDATE sys_permission SET parent_id = 1100, path = 'operlog'  WHERE id = 700;
UPDATE sys_permission SET parent_id = 1100, path = 'loginlog' WHERE id = 800;
UPDATE sys_permission SET parent_id = 1100, path = 'online'   WHERE id = 900;

-- 4. 运行监控目录绑给 tech_admin（admin 走通配无需）
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 3, 1100
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 3 AND rp.perm_id = 1100);
