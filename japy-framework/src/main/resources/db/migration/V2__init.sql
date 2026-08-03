-- ============================================================
-- japy-framework V1：基础框架表结构 + RBAC 预置数据
-- 数据库：japy_moments（与 moments 业务表共存，前缀 sys_ 隔离）
-- ============================================================

-- 用户
CREATE TABLE sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50)  NOT NULL,
    avatar      VARCHAR(500),              -- 头像（SVG 或 URL）
    email       VARCHAR(100),
    phone       VARCHAR(20),
    sex         SMALLINT DEFAULT 0,        -- 0未知 1男 2女
    status      SMALLINT DEFAULT 0,        -- 0正常 1停用
    del_flag    SMALLINT DEFAULT 0,        -- 0存在 1删除
    create_by   BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 角色
CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    role_name   VARCHAR(50) NOT NULL,
    role_key    VARCHAR(50) NOT NULL UNIQUE,
    sort        INT DEFAULT 0,
    status      SMALLINT DEFAULT 0,
    remark      VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 权限（菜单/按钮统一：1目录 2菜单 3按钮）
CREATE TABLE sys_permission (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT DEFAULT 0,
    perm_name   VARCHAR(50) NOT NULL,
    perm_key    VARCHAR(100),              -- 权限标识，如 system:user:list
    perm_type   SMALLINT DEFAULT 1,        -- 1目录 2菜单 3按钮
    path        VARCHAR(200),              -- 前端路由
    component   VARCHAR(200),
    icon        VARCHAR(50),
    sort        INT DEFAULT 0,
    status      SMALLINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户-角色
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 角色-权限
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    perm_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, perm_id)
);

-- 字典类型
CREATE TABLE sys_dict_type (
    id          BIGSERIAL PRIMARY KEY,
    dict_name   VARCHAR(100) NOT NULL,
    dict_type   VARCHAR(100) NOT NULL UNIQUE,
    status      SMALLINT DEFAULT 0,
    remark      VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 字典数据
CREATE TABLE sys_dict_data (
    id          BIGSERIAL PRIMARY KEY,
    dict_type   VARCHAR(100) NOT NULL,
    dict_label  VARCHAR(100) NOT NULL,
    dict_value  VARCHAR(100) NOT NULL,
    sort        INT DEFAULT 0,
    status      SMALLINT DEFAULT 0,
    remark      VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 参数配置
CREATE TABLE sys_config (
    id           BIGSERIAL PRIMARY KEY,
    config_name  VARCHAR(100) NOT NULL,
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500),
    remark       VARCHAR(500),
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 公告
CREATE TABLE sys_notice (
    id            BIGSERIAL PRIMARY KEY,
    notice_title  VARCHAR(100) NOT NULL,
    notice_type   SMALLINT DEFAULT 1,      -- 1通知 2公告
    notice_content TEXT,
    status        SMALLINT DEFAULT 0,      -- 0正常 1关闭
    create_by     BIGINT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 操作日志
CREATE TABLE sys_oper_log (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(100),            -- 模块标题
    business_type SMALLINT DEFAULT 0,      -- 业务类型 0其他 1新增 2修改 3删除
    method        VARCHAR(200),            -- 方法名
    request_method VARCHAR(10),            -- GET/POST...
    oper_name     VARCHAR(50),             -- 操作人
    oper_url      VARCHAR(255),
    oper_ip       VARCHAR(50),
    oper_param    TEXT,                    -- 请求参数
    json_result   TEXT,
    status        SMALLINT DEFAULT 0,      -- 0成功 1失败
    error_msg     TEXT,
    cost_time     BIGINT,                  -- 耗时（ms）
    oper_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 登录日志
CREATE TABLE sys_login_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50),
    ipaddr      VARCHAR(50),
    status      SMALLINT DEFAULT 0,        -- 0成功 1失败
    msg         VARCHAR(255),
    login_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 预置数据
-- ============================================================

-- 管理员账号：admin / admin123（BCrypt，与 moments 同 hash）
INSERT INTO sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2b$12$RVM5zrsD2qksFVZgQ7rNVOdK4sKo1bWXrljVkZ3F4MGblXA4Owve.', '管理员', 0);

-- 角色
INSERT INTO sys_role (id, role_name, role_key, sort) VALUES
(1, '超级管理员', 'admin', 1),
(2, '普通用户', 'user', 2);

-- 权限树（目录/菜单/按钮）
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, icon, sort) VALUES
(1,  0,  '系统管理', NULL,            1, '/system', 'Setting', 1),
(2,  0,  '仪表盘',   'dashboard:view', 2, '/dashboard', 'Odometer', 0),

(100, 1, '用户管理', NULL, 2, '/system/user', 'User', 1),
(101, 100, '用户查询', 'system:user:list', 3, NULL, NULL, 1),
(102, 100, '用户新增', 'system:user:add', 3, NULL, NULL, 2),
(103, 100, '用户修改', 'system:user:edit', 3, NULL, NULL, 3),
(104, 100, '用户删除', 'system:user:delete', 3, NULL, NULL, 4),
(105, 100, '重置密码', 'system:user:resetPwd', 3, NULL, NULL, 5),
(106, 100, '分配角色', 'system:user:assignRole', 3, NULL, NULL, 6),
(107, 100, '用户状态', 'system:user:status', 3, NULL, NULL, 7),

(200, 1, '角色管理', NULL, 2, '/system/role', 'Avatar', 2),
(201, 200, '角色查询', 'system:role:list', 3, NULL, NULL, 1),
(202, 200, '角色新增', 'system:role:add', 3, NULL, NULL, 2),
(203, 200, '角色修改', 'system:role:edit', 3, NULL, NULL, 3),
(204, 200, '角色删除', 'system:role:delete', 3, NULL, NULL, 4),
(205, 200, '分配权限', 'system:role:assignPerm', 3, NULL, NULL, 5),

(300, 1, '权限管理', NULL, 2, '/system/perm', 'Menu', 3),
(301, 300, '权限查询', 'system:perm:list', 3, NULL, NULL, 1),
(302, 300, '权限新增', 'system:perm:add', 3, NULL, NULL, 2),
(303, 300, '权限修改', 'system:perm:edit', 3, NULL, NULL, 3),
(304, 300, '权限删除', 'system:perm:delete', 3, NULL, NULL, 4),

(400, 1, '字典管理', NULL, 2, '/system/dict', 'Notebook', 4),
(401, 400, '字典查询', 'system:dict:list', 3, NULL, NULL, 1),
(402, 400, '字典新增', 'system:dict:add', 3, NULL, NULL, 2),
(403, 400, '字典修改', 'system:dict:edit', 3, NULL, NULL, 3),
(404, 400, '字典删除', 'system:dict:delete', 3, NULL, NULL, 4),

(500, 1, '参数管理', NULL, 2, '/system/config', 'Tools', 5),
(501, 500, '参数查询', 'system:config:list', 3, NULL, NULL, 1),
(502, 500, '参数新增', 'system:config:add', 3, NULL, NULL, 2),
(503, 500, '参数修改', 'system:config:edit', 3, NULL, NULL, 3),
(504, 500, '参数删除', 'system:config:delete', 3, NULL, NULL, 4),

(600, 1, '公告管理', NULL, 2, '/system/notice', 'Bell', 6),
(601, 600, '公告查询', 'system:notice:list', 3, NULL, NULL, 1),
(602, 600, '公告新增', 'system:notice:add', 3, NULL, NULL, 2),
(603, 600, '公告修改', 'system:notice:edit', 3, NULL, NULL, 3),
(604, 600, '公告删除', 'system:notice:delete', 3, NULL, NULL, 4),

(700, 1, '操作日志', NULL, 2, '/system/operlog', 'Document', 7),
(701, 700, '日志查询', 'system:operlog:list', 3, NULL, NULL, 1),
(702, 700, '日志清空', 'system:operlog:clean', 3, NULL, NULL, 2),

(800, 1, '登录日志', NULL, 2, '/system/loginlog', 'Key', 8),
(801, 800, '日志查询', 'system:loginlog:list', 3, NULL, NULL, 1),

(900, 1, '在线用户', NULL, 2, '/system/online', 'Monitor', 9),
(901, 900, '在线查询', 'system:online:list', 3, NULL, NULL, 1),
(902, 900, '强制下线', 'system:online:forceLogout', 3, NULL, NULL, 2);

-- admin 角色绑定全部权限
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission;

-- admin 用户绑定 admin 角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 字典预置
INSERT INTO sys_dict_type (dict_name, dict_type) VALUES
('用户状态', 'sys_user_status'),
('性别', 'sys_user_sex'),
('公告类型', 'sys_notice_type'),
('操作类型', 'sys_oper_type'),
('是否', 'sys_yes_no');

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, sort) VALUES
('sys_user_status', '正常', '0', 1),
('sys_user_status', '停用', '1', 2),
('sys_user_sex', '未知', '0', 1),
('sys_user_sex', '男', '1', 2),
('sys_user_sex', '女', '2', 3),
('sys_notice_type', '通知', '1', 1),
('sys_notice_type', '公告', '2', 2),
('sys_oper_type', '其他', '0', 1),
('sys_oper_type', '新增', '1', 2),
('sys_oper_type', '修改', '2', 3),
('sys_oper_type', '删除', '3', 4),
('sys_yes_no', '是', '1', 1),
('sys_yes_no', '否', '0', 2);

-- 参数预置
INSERT INTO sys_config (config_name, config_key, config_value, remark) VALUES
('登录失败锁定次数', 'login.maxFail', '5', '连续失败 N 次锁定'),
('登录锁定时长(分钟)', 'login.lockMinutes', '30', '锁定时长'),
('默认头像底色', 'avatar.bgColors', '#3b6ef6,#16a34a,#d97706,#dc2626,#7c5cf0', '头像生成配色');
