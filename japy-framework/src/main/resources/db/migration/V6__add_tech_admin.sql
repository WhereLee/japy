-- ============================================================
-- V6: 技术管理员角色（tech_admin）+ AI 运维提升为顶级菜单
-- 架构依据（调研若依 RuoYi）：
--   1) 权限树三级：1目录/2菜单/3按钮，parent_id=0 顶级
--   2) 业务管理员(admin)与技术管理员(tech_admin)职责分离
--   3) admin 走通配权限 *:*:*（代码层实现，见 UserDetailsServiceImpl），
--      不依赖逐条绑定，避免"新增权限点后忘绑 admin"的隐患
-- 全部幂等：WHERE NOT EXISTS 防重复执行（测试库历史数据场景）
-- ============================================================

-- 1. AI 运维提升为顶级菜单（原挂系统管理 id=1 下，tech_admin 无系统管理目录不可见）
UPDATE sys_permission SET parent_id = 0 WHERE id = 1000;

-- 2. 技术管理员角色
INSERT INTO sys_role (id, role_name, role_key, sort)
SELECT 3, '技术管理员', 'tech_admin', 3
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'tech_admin');

-- 3. 绑定权限：AI 运维全套 + 仪表盘 + 日志/在线用户查看（不含日志清空——审计痕迹归业务管理员）
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission
WHERE id IN (2,                          -- 仪表盘
             1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007,  -- AI 运维（目录+按钮）
             700, 701,                   -- 操作日志（查询，不含清空 702）
             800, 801,                   -- 登录日志（查询）
             900, 901, 902)              -- 在线用户（查询+强制下线）
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 3 AND rp.perm_id = sys_permission.id);

-- 4. 技术管理员演示账号 tech / tech123456（CTE 拿自增 id，避免撞历史数据主键）
WITH u AS (
    INSERT INTO sys_user (username, password, nickname, status)
    SELECT 'tech', '$2b$12$vBJ9Fq5/0D6zHPN390ul4.IQtgWlO2CtVNgkPwvEFXzlrwZV0CHUe', '技术管理员', 0
    WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'tech')
    RETURNING id
)
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 3 FROM u
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role ur
                  WHERE ur.user_id = (SELECT id FROM u) AND ur.role_id = 3);
