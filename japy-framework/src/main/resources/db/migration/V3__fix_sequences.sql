-- 修复：预置数据指定 id 后未同步序列（BIGSERIAL 从 1 开始导致主键冲突）
SELECT setval('sys_user_id_seq', (SELECT MAX(id) FROM sys_user));
SELECT setval('sys_role_id_seq', (SELECT MAX(id) FROM sys_role));
SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_dict_type_id_seq', (SELECT MAX(id) FROM sys_dict_type));
SELECT setval('sys_dict_data_id_seq', (SELECT MAX(id) FROM sys_dict_data));
SELECT setval('sys_config_id_seq', (SELECT MAX(id) FROM sys_config));
SELECT setval('sys_notice_id_seq', (SELECT MAX(id) FROM sys_notice));
SELECT setval('sys_oper_log_id_seq', (SELECT MAX(id) FROM sys_oper_log));
SELECT setval('sys_login_log_id_seq', (SELECT MAX(id) FROM sys_login_log));
