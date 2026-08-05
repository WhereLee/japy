-- ============================================================
-- V11: 小说对象生命周期（状态机 + 文件存储 + 管理端菜单）
-- status 语义扩展：
--   0 连载（可阅读） 1 完结（可阅读） 2 草稿（解析中/未上架） 3 已下架
-- del_flag：0 正常 1 逻辑删除（@TableLogic）
-- 文件目录：data/novels/{novelId}_{title}/{source.txt, meta.json, chapters/}
-- ============================================================

-- 1. jf_novel 扩展字段
ALTER TABLE jf_novel ADD COLUMN file_path VARCHAR(500);
ALTER TABLE jf_novel ADD COLUMN del_flag SMALLINT DEFAULT 0;

-- 2. seed 小说（星海征途）置为连载（status=0 已是默认，显式声明）
UPDATE jf_novel SET status = 0, del_flag = 0 WHERE id = 1;

-- 3. 管理端菜单：小说管理（顶级目录 + 列表菜单 + 按钮权限）
-- 权限点供 @ss.hasPermi 使用；admin 走 *:*:* 通配自动可见，无需绑定
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1200, 0, '小说管理', NULL, 1, '/novel-admin', NULL, 'Reading', 3
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1200);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1201, 1200, '小说列表', 'novel:list', 2, 'list', 'novel/list/index', 'Collection', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1201);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1202, 1201, '小说上传', 'novel:upload', 3, NULL, NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1202);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1203, 1201, '状态操作', 'novel:status', 3, NULL, NULL, NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1203);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1204, 1201, '小说删除', 'novel:delete', 3, NULL, NULL, NULL, 3
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1204);
