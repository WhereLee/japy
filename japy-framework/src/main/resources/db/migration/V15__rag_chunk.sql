-- ============================================================
-- V15: RAG 检索块表（pgvector 向量存储）
-- 事实源：jf_novel_paragraph（自然段）→ Python 切块/编码 → 写本表
-- 检索：Python 端 SQL `ORDER BY embedding <=> 查询向量`（余弦距离）
-- strategy: paragraph(自然段优先+二分+引号保护) / sentence / fixed
-- 状态: 0待向量化 1已向量化 2失败
-- ============================================================

-- 启用 pgvector（幂等；开发库已启用，测试库重建时创建）
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE jf_rag_chunk (
    id          BIGSERIAL PRIMARY KEY,
    novel_id    BIGINT NOT NULL,
    chapter_no  INT    NOT NULL,
    para_seq    INT    NOT NULL,           -- 来源自然段序号（可回溯 jf_novel_paragraph）
    chunk_seq   INT    NOT NULL,           -- 章内块序号（>500 字二分时一段多块）
    content     TEXT   NOT NULL,
    chars       INT    DEFAULT 0,
    strategy    VARCHAR(20) DEFAULT 'paragraph',
    embedding   vector(768),               -- bge-base-zh-v1.5 输出维度
    status      SMALLINT DEFAULT 0,        -- 0待向量化 1已向量化 2失败
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rag_chunk_novel ON jf_rag_chunk (novel_id);
-- 向量索引：HNSW（余弦距离），仅对已向量化行
CREATE INDEX idx_rag_chunk_embedding ON jf_rag_chunk
    USING hnsw (embedding vector_cosine_ops);
COMMENT ON TABLE jf_rag_chunk IS 'RAG 检索块（向量存储，pgvector）';

-- 管理端权限点：rag 同步/状态（admin 通配自动可见，tech_admin 不可见）
INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1208, 1200, 'RAG 索引', 'rag:list', 2, 'rag', 'novel/rag/index', 'Connection', 3
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1208);

INSERT INTO sys_permission (id, parent_id, perm_name, perm_key, perm_type, path, component, icon, sort)
SELECT 1209, 1208, 'RAG 同步', 'rag:sync', 3, NULL, NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1209);
