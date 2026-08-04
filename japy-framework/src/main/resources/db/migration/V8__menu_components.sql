-- ============================================================
-- V8: 补齐系统管理菜单的 component（前端组件路径）
-- 问题：V2 建菜单时未写 component 列，前端动态路由无法映射页面组件。
-- 路径约定：component = 前端 views 下的相对路径（不含 .vue）
-- ============================================================

UPDATE sys_permission SET component = 'dashboard/index'      WHERE id = 2;
UPDATE sys_permission SET component = 'system/user/index'    WHERE id = 100;
UPDATE sys_permission SET component = 'system/role/index'    WHERE id = 200;
UPDATE sys_permission SET component = 'system/perm/index'    WHERE id = 300;
UPDATE sys_permission SET component = 'system/dict/index'    WHERE id = 400;
UPDATE sys_permission SET component = 'system/config/index'  WHERE id = 500;
UPDATE sys_permission SET component = 'system/notice/index'  WHERE id = 600;
UPDATE sys_permission SET component = 'monitor/operlog/index'  WHERE id = 700;
UPDATE sys_permission SET component = 'monitor/loginlog/index' WHERE id = 800;
UPDATE sys_permission SET component = 'monitor/online/index'   WHERE id = 900;
