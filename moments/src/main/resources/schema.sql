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

-- 操作日志（管理端，AOP 自动记录）
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(20),
    target_id BIGINT,
    detail VARCHAR(500),
    cost_ms INT DEFAULT 0,          -- 操作耗时（毫秒）
    method VARCHAR(200),            -- 请求方法与路径，如 POST /api/admin/users/5/ban
    ip VARCHAR(50),
    error VARCHAR(500),             -- 失败时的错误信息
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 兼容旧库（启动时补列）
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS cost_ms INT DEFAULT 0;
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS method VARCHAR(200);
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS ip VARCHAR(50);
ALTER TABLE operation_log ADD COLUMN IF NOT EXISTS error VARCHAR(500);

-- 小说（管理端上传入库）
CREATE TABLE IF NOT EXISTS novel (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL UNIQUE,
    author VARCHAR(50),
    status SMALLINT DEFAULT 0,          -- 0建设中 1已入库 2入库失败
    chapter_count INT DEFAULT 0,
    paragraph_count INT DEFAULT 0,
    total_chars INT DEFAULT 0,
    source_name VARCHAR(200),           -- 源文件名
    source_size BIGINT DEFAULT 0,       -- 源文件大小（字节）
    source_encoding VARCHAR(20),        -- 源文件编码
    dir_path VARCHAR(500),              -- 落盘目录
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_novel_status ON novel(status);
-- 兼容旧库（启动时补列）
ALTER TABLE novel ADD COLUMN IF NOT EXISTS source_name VARCHAR(200);
ALTER TABLE novel ADD COLUMN IF NOT EXISTS source_size BIGINT DEFAULT 0;
ALTER TABLE novel ADD COLUMN IF NOT EXISTS source_encoding VARCHAR(20);
ALTER TABLE novel ADD COLUMN IF NOT EXISTS dir_path VARCHAR(500);

-- 小说章节（含统计信息）
CREATE TABLE IF NOT EXISTS novel_chapter (
    id BIGSERIAL PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    chars INT DEFAULT 0,
    paragraph_count INT DEFAULT 0,
    max_para_chars INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, chapter_no)
);
CREATE INDEX IF NOT EXISTS idx_chapter_novel ON novel_chapter(novel_id);

-- 小说段落（引用原文检索的数据底座）
CREATE TABLE IF NOT EXISTS novel_paragraph (
    id BIGSERIAL PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    para_seq INT NOT NULL,
    content TEXT NOT NULL,
    chars INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, chapter_no, para_seq)
);
CREATE INDEX IF NOT EXISTS idx_para_novel ON novel_paragraph(novel_id, chapter_no);

-- 预置管理员账号（密码: admin123）
INSERT INTO app_user (id, username, password, nickname, role, status) VALUES
(1, 'admin', '$2b$12$RVM5zrsD2qksFVZgQ7rNVOdK4sKo1bWXrljVkZ3F4MGblXA4Owve.', '管理员', 'admin', 0)
ON CONFLICT DO NOTHING;

SELECT setval('app_user_id_seq', (SELECT MAX(id) FROM app_user));
