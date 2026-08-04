-- ============================================================
-- japy-framework V5：操作日志幂等（trace_id 唯一索引）
-- 场景：MQ 降级 Redis Stream 后重放/重复消费时防重复落库
-- ============================================================
ALTER TABLE sys_oper_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_oper_log_trace ON sys_oper_log(trace_id) WHERE trace_id IS NOT NULL;
