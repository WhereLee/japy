CREATE DATABASE IF NOT EXISTS japy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE japy;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0=正常 1=封禁',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS novel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    author VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    user_id BIGINT,
    nickname VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    quote_text VARCHAR(500),
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    status TINYINT DEFAULT 0 COMMENT '0=正常 1=隐藏 2=删除',
    pinned TINYINT DEFAULT 0 COMMENT '0=普通 1=置顶',
    featured TINYINT DEFAULT 0 COMMENT '0=普通 1=加精',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_novel_id (novel_id),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT,
    nickname VARCHAR(50) NOT NULL,
    reply_to VARCHAR(50),
    content TEXT NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0=正常 1=隐藏 2=删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_post_id (post_id),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS post_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_nickname (post_id, nickname)
);

-- 预置小说数据
INSERT IGNORE INTO novel (id, title, author) VALUES
(1, '龙族2·悼亡者之瞳', '江南'),
(2, '天龙八部（世纪新修版）', '金庸'),
(3, '斗破苍穹', '天蚕土豆');
