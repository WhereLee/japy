CREATE TABLE IF NOT EXISTS novel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(100) DEFAULT '',
    description TEXT,
    file_name VARCHAR(200) NOT NULL UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    nickname VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL DEFAULT '',
    role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'user/admin',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    login_fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_time DATETIME COMMENT '锁定截止时间',
    last_login_at DATETIME COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chapter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    novel_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT,
    chapter_order INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_novel_order (novel_id, chapter_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS annotation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chapter_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    anchor_start INT NOT NULL,
    anchor_end INT NOT NULL,
    selected_text VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    type TINYINT NOT NULL DEFAULT 0 COMMENT '0=普通批注 1=数据校验',
    like_count INT NOT NULL DEFAULT 0 COMMENT '冗余点赞数，避免 COUNT 查询',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '冗余评论数，避免 COUNT 查询',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 联合索引：按章节查询批注时，同时按偏移量排序，覆盖最常用的查询模式
    INDEX idx_chapter_anchor (chapter_id, anchor_start),
    -- 联合索引：个人主页查询（按用户 + 时间倒序）+ 热门批注查询（按时间 + 点赞数）
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_created_likes (created_at, like_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS annotation_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    annotation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reply_to_id BIGINT NULL COMMENT '回复哪条评论ID，NULL=直接评论批注',
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 联合索引：按批注查评论并按时间排序
    INDEX idx_annotation_created (annotation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS annotation_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    annotation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_annotation_user (annotation_id, user_id),
    INDEX idx_annotation (annotation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表：AOP 异步写入，记录所有关键操作
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    module VARCHAR(50) NOT NULL DEFAULT '' COMMENT '模块名',
    operation VARCHAR(100) NOT NULL DEFAULT '' COMMENT '操作描述',
    method VARCHAR(200) NOT NULL DEFAULT '' COMMENT '类名.方法名',
    request_method VARCHAR(10) NOT NULL DEFAULT '' COMMENT 'HTTP 方法',
    request_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '请求 URL',
    request_params TEXT COMMENT '请求参数（脱敏后）',
    response_result TEXT COMMENT '响应结果',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL',
    error_message TEXT COMMENT '异常信息',
    execute_time BIGINT NOT NULL DEFAULT 0 COMMENT '执行耗时(ms)',
    operator_id BIGINT COMMENT '操作人 ID',
    operator_name VARCHAR(50) COMMENT '操作人用户名',
    ip VARCHAR(50) NOT NULL DEFAULT '' COMMENT '客户端 IP',
    user_agent VARCHAR(500) DEFAULT '' COMMENT '浏览器 UA',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operator (operator_id),
    -- 联合索引：管理端日志查询（按模块 + 状态筛选 + 时间排序）
    INDEX idx_module_status_created (module, status, created_at),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 内容举报表
CREATE TABLE IF NOT EXISTS content_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL COMMENT '举报人ID',
    target_type VARCHAR(20) NOT NULL COMMENT 'annotation/comment',
    target_id BIGINT NOT NULL COMMENT '举报目标ID',
    reason TEXT NOT NULL COMMENT '举报原因',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/resolved/rejected',
    handler_id BIGINT COMMENT '处理人ID',
    handle_note TEXT COMMENT '处理备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reporter (reporter_id),
    -- 联合索引：管理端举报列表（按状态筛选 + 时间排序）
    INDEX idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 站内通知表：管理员操作触发，用户登录后可见
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    type VARCHAR(30) NOT NULL COMMENT 'ban/unban/password_reset/report_resolved/report_rejected/like/comment/reply',
    title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '通知标题',
    content TEXT NOT NULL COMMENT '通知内容',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
    related_id BIGINT COMMENT '关联业务ID（举报ID等）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 批注收藏表：用户收藏有价值的批注
CREATE TABLE IF NOT EXISTS annotation_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '收藏用户ID',
    annotation_id BIGINT NOT NULL COMMENT '被收藏的批注ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_annotation (user_id, annotation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
