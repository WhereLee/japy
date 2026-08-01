-- PostgreSQL + pgvector schema
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar VARCHAR(255),
    bio VARCHAR(200),
    role VARCHAR(20) DEFAULT 'user',
    points INT DEFAULT 0,
    level INT DEFAULT 0,
    status SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS novel (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    author VARCHAR(50),
    chapter_count INT DEFAULT 0,
    chunk_count INT DEFAULT 0,
    import_status SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS post (
    id BIGSERIAL PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    user_id BIGINT,
    nickname VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    quote_text VARCHAR(500),
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    status SMALLINT DEFAULT 0,
    pinned SMALLINT DEFAULT 0,
    featured SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_post_novel ON post(novel_id);
CREATE INDEX IF NOT EXISTS idx_post_created ON post(created_at);
CREATE INDEX IF NOT EXISTS idx_post_status ON post(status);

CREATE TABLE IF NOT EXISTS comment (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT,
    nickname VARCHAR(50) NOT NULL,
    reply_to VARCHAR(50),
    content TEXT NOT NULL,
    status SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_comment_post ON comment(post_id);
CREATE INDEX IF NOT EXISTS idx_comment_status ON comment(status);

CREATE TABLE IF NOT EXISTS post_like (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, nickname)
);

CREATE TABLE IF NOT EXISTS post_favorite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, post_id)
);

CREATE TABLE IF NOT EXISTS user_block (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, blocked_user_id)
);

CREATE TABLE IF NOT EXISTS sensitive_word (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS report (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500),
    status SMALLINT DEFAULT 0,
    result VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_report_status ON report(status);

CREATE TABLE IF NOT EXISTS notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    ref_type VARCHAR(20),
    ref_id BIGINT,
    content VARCHAR(300),
    is_read SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notif_user_read ON notification(user_id, is_read);

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(20),
    target_id BIGINT,
    detail VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS points_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    points INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_points_user_date ON points_log(user_id, created_at);

-- 小说原文切块表（RAG复用）
CREATE TABLE IF NOT EXISTS novel_chunk (
    id BIGSERIAL PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    chapter_title VARCHAR(100),
    seq_in_chapter INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chunk_novel_chapter ON novel_chunk(novel_id, chapter_no, seq_in_chapter);

-- 预置小说数据
INSERT INTO novel (id, title, author) VALUES
(1, '龙族2·悼亡者之瞳', '江南'),
(2, '天龙八部（世纪新修版）', '金庸'),
(3, '斗破苍穹', '天蚕土豆')
ON CONFLICT DO NOTHING;

-- 预置管理员账号（密码: admin123）
INSERT INTO app_user (id, username, password, nickname, role, status) VALUES
(1, 'admin', '$2b$12$RVM5zrsD2qksFVZgQ7rNVOdK4sKo1bWXrljVkZ3F4MGblXA4Owve.', '管理员', 'admin', 0)
ON CONFLICT DO NOTHING;

-- 重置序列（因为手动指定了id）
SELECT setval('novel_id_seq', (SELECT MAX(id) FROM novel));
SELECT setval('app_user_id_seq', (SELECT MAX(id) FROM app_user));
