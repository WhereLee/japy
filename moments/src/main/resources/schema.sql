-- ============================================================
-- japy_moments · 标准QQ空间/朋友圈式动态社区
-- 使用前请先创建数据库：CREATE DATABASE japy_moments;
-- ============================================================

-- 用户
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar VARCHAR(255),
    bio VARCHAR(200),
    role VARCHAR(20) DEFAULT 'user',
    status SMALLINT DEFAULT 0,          -- 0正常 1封禁
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 动态（说说）
CREATE TABLE IF NOT EXISTS moment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(50) NOT NULL,      -- 发布时快照
    content TEXT NOT NULL,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    status SMALLINT DEFAULT 0,          -- 0正常 1隐藏 2删除
    pinned SMALLINT DEFAULT 0,          -- 置顶
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_moment_created ON moment(created_at);
CREATE INDEX IF NOT EXISTS idx_moment_status ON moment(status);
CREATE INDEX IF NOT EXISTS idx_moment_user ON moment(user_id);

-- 点赞（带赞过的人）
CREATE TABLE IF NOT EXISTS moment_like (
    id BIGSERIAL PRIMARY KEY,
    moment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(50),               -- 点赞时快照，用于赞列表
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (moment_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_moment_like_moment ON moment_like(moment_id);

-- 评论（支持一层楼中楼：parent_id 指向顶层评论）
CREATE TABLE IF NOT EXISTS comment (
    id BIGSERIAL PRIMARY KEY,
    moment_id BIGINT NOT NULL,
    user_id BIGINT,
    nickname VARCHAR(50) NOT NULL,
    parent_id BIGINT,                   -- NULL=顶层评论，否则回复某条顶层评论
    reply_to VARCHAR(50),               -- 被回复人昵称快照
    content TEXT NOT NULL,
    status SMALLINT DEFAULT 0,          -- 0正常 1隐藏 2删除
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_comment_moment ON comment(moment_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent ON comment(parent_id);

-- 通知
CREATE TABLE IF NOT EXISTS notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,          -- like / comment / reply / report_result / announce
    ref_type VARCHAR(20),               -- moment / comment
    ref_id BIGINT,
    content VARCHAR(300),
    is_read SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notif_user_read ON notification(user_id, is_read);

-- 举报
CREATE TABLE IF NOT EXISTS report (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,   -- moment / comment
    target_id BIGINT NOT NULL,
    reason VARCHAR(500),
    status SMALLINT DEFAULT 0,          -- 0待处理 1已处理 2已驳回
    result VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_report_status ON report(status);

-- 敏感词
CREATE TABLE IF NOT EXISTS sensitive_word (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(100) NOT NULL UNIQUE
);

-- 操作日志（管理端）
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(20),
    target_id BIGINT,
    detail VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 预置管理员账号（密码: admin123）
INSERT INTO app_user (id, username, password, nickname, role, status) VALUES
(1, 'admin', '$2b$12$RVM5zrsD2qksFVZgQ7rNVOdK4sKo1bWXrljVkZ3F4MGblXA4Owve.', '管理员', 'admin', 0)
ON CONFLICT DO NOTHING;

SELECT setval('app_user_id_seq', (SELECT MAX(id) FROM app_user));
