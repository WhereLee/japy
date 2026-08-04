# 运行手册（RUNBOOK）

## 一、运行前必查：中间件端口

```bash
# 三项都要有 LISTEN，缺哪个补哪个
netstat -ano | findstr ":6379"    # Redis
netstat -ano | findstr ":9876"    # RocketMQ NameServer
netstat -ano | findstr ":10911"   # RocketMQ Broker
```

| 中间件 | 端口 | 路径 | 启动方式 |
|---|---|---|---|
| Redis | 6379 | `F:\Redis\redis-server.exe` | 通常已在跑；未跑则启动该 exe（不要用 bat） |
| RocketMQ NameServer | 9876 | `D:\rocketmq-5.3.1\rocketmq-all-5.3.1-bin-release\bin\mqnamesrv.cmd` | `mq-start.bat` 或下方 agent 命令 |
| RocketMQ Broker | 10911 | 同上 `mqbroker.cmd -n localhost:9876` | `mq-start.bat` 或下方 agent 命令 |

**RocketMQ 两个组件必须同时启动才能使用**（只起一个无法收发消息）。

## 二、用户启动（推荐）

双击项目根目录：
- `mq-start.bat` —— 一键启动 NameServer + Broker（含端口占用检测，已在运行则跳过）
- `start-all.bat` —— Redis + RocketMQ + 后端 8085 全起
- `stop-all.bat` —— 按窗口标题全部停止

用户双击的进程独立于任何 shell 会话，最稳定。

## 三、Agent 启动（已实测：Agent 无法可靠保持 MQ 进程）

> **结论（2026-08 实测）**：Agent 用 bash 调 `cmd //c '...mqnamesrv.cmd' &` 能短暂启动（端口 LISTEN、日志 boot success），但 **bash 命令结束的瞬间工具会清理整个进程组，MQ 进程立即被杀**——netstat 从 LISTEN 变无，TCP 连接被拒（WinError 10061）。
> 因此 **MQ 必须由用户双击 `mq-start.bat` 启动**，进程归 Windows 管理才能持续存活。

Agent 若被要求尝试启动，正确验证方式：
```bash
# 启动（可能短暂成功）
cd /d/rocketmq-5.3.1/rocketmq-all-5.3.1-bin-release/bin && cmd //c 'set ROCKETMQ_HOME=D:\rocketmq-5.3.1\rocketmq-all-5.3.1-bin-release&& mqnamesrv.cmd' > /tmp/mq_ns.log 2>&1 &
# 必须用 TCP 实测验证，不能只看 netstat/boot success
python -c "import socket; s=socket.socket(); s.settimeout(3); s.connect(('127.0.0.1',9876)); print('OK')"
```


## 四、验证 MQ 链路真实可用

```bash
# 1. 标准探针（namesrv 存活时可用）
cd /d/rocketmq-5.3.1/rocketmq-all-5.3.1-bin-release/bin && cmd //c 'set ROCKETMQ_HOME=D:\rocketmq-5.3.1\rocketmq-all-5.3.1-bin-release&& mqadmin clusterList -n 127.0.0.1:9876'
#   应显示 Broker ACTIVATED=true

# 2. 后端日志（注意：中文是 GBK 乱码，用英文关键词搜索！）
#   grep "Producer" /tmp/framework_boot.log   → "RocketMQ Producer 启动成功: localhost:9876"
#   grep "OperLogConsumer" /tmp/framework_boot.log → "操作日志消费者启动成功 ... topic=japy_oper_log"
#   ⚠️ 不要用中文关键词 grep（控制台 GBK 乱码会匹配不到）

# 3. 发一个写请求（如 POST /ai/events/run），查 sys_oper_log 有新增 = MQ 异步链路真实消费
```
