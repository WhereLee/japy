-- ============================================================
-- V12: 同步序列（seed 显式 id 导致序列落后，新插入主键冲突）
-- 修复：V10 seed 用显式 id=1 插入，BIGSERIAL 序列未跟进，
--       新行 nextval 仍返回 1 → 主键冲突。
-- 标准做法：已应用的迁移不修改，追加修复迁移 setval。
-- ============================================================

SELECT setval('jf_novel_id_seq', (SELECT COALESCE(MAX(id), 1) FROM jf_novel));
SELECT setval('jf_novel_chapter_id_seq', (SELECT COALESCE(MAX(id), 1) FROM jf_novel_chapter));
SELECT setval('jf_novel_paragraph_id_seq', (SELECT COALESCE(MAX(id), 1) FROM jf_novel_paragraph));
SELECT setval('jf_novel_read_progress_id_seq', (SELECT COALESCE(MAX(id), 1) FROM jf_novel_read_progress));
