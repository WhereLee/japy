#!/bin/bash
# ============================================================
# ReCloud JVM 监控脚本
# ============================================================
# 使用 jstat/jmap/jstack 采集运行指标
# 用法: ./jvm-monitor.sh [PID]
# 如果不传 PID，会自动查找 recloud 进程
# ============================================================

LOG_DIR="$(dirname "$0")/../logs"
mkdir -p "$LOG_DIR"
MONITOR_LOG="$LOG_DIR/jvm-monitor-$(date +%Y%m%d).log"

# 获取 PID
if [ -n "$1" ]; then
    PID=$1
else
    PID=$(pgrep -f "recloud-1.0.0.jar" | head -1)
fi

if [ -z "$PID" ]; then
    echo "ERROR: 找不到 recloud 进程，请手动指定 PID"
    echo "用法: $0 <PID>"
    exit 1
fi

echo "============================================" | tee -a "$MONITOR_LOG"
echo "  ReCloud JVM Monitor - $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "$MONITOR_LOG"
echo "  PID: $PID" | tee -a "$MONITOR_LOG"
echo "============================================" | tee -a "$MONITOR_LOG"

# ---- 1. 堆内存使用情况 ----
echo "" | tee -a "$MONITOR_LOG"
echo "[1] 堆内存使用情况 (jmap -heap):" | tee -a "$MONITOR_LOG"
echo "-------------------------------------------" | tee -a "$MONITOR_LOG"
jmap -heap "$PID" 2>/dev/null | tee -a "$MONITOR_LOG"

# ---- 2. GC 统计 ----
echo "" | tee -a "$MONITOR_LOG"
echo "[2] GC 统计 (jstat -gcutil 1000 3):" | tee -a "$MONITOR_LOG"
echo "-------------------------------------------" | tee -a "$MONITOR_LOG"
echo "  S0    S1     E      O      M     CCS    YGC   YGCT   FGC   FGCT    GCT" | tee -a "$MONITOR_LOG"
jstat -gcutil "$PID" 1000 3 2>/dev/null | tee -a "$MONITOR_LOG"

# ---- 3. 类加载统计 ----
echo "" | tee -a "$MONITOR_LOG"
echo "[3] 类加载统计 (jstat -class):" | tee -a "$MONITOR_LOG"
echo "-------------------------------------------" | tee -a "$MONITOR_LOG"
jstat -class "$PID" 2>/dev/null | tee -a "$MONITOR_LOG"

# ---- 4. 线程数 ----
echo "" | tee -a "$MONITOR_LOG"
echo "[4] 线程统计:" | tee -a "$MONITOR_LOG"
echo "-------------------------------------------" | tee -a "$MONITOR_LOG"
THREAD_COUNT=$(jstack "$PID" 2>/dev/null | grep -c "^\"" || echo "N/A")
echo "  总线程数: $THREAD_COUNT" | tee -a "$MONITOR_LOG"

# 按状态分组统计
echo "  线程状态分布:" | tee -a "$MONITOR_LOG"
jstack "$PID" 2>/dev/null | grep "java.lang.Thread.State" | sort | uniq -c | sort -rn | tee -a "$MONITOR_LOG"

# ---- 5. 死锁检测 ----
echo "" | tee -a "$MONITOR_LOG"
echo "[5] 死锁检测:" | tee -a "$MONITOR_LOG"
echo "-------------------------------------------" | tee -a "$MONITOR_LOG"
DEADLOCK=$(jstack "$PID" 2>/dev/null | grep -c "Found one Java-level deadlock" || echo "0")
if [ "$DEADLOCK" -gt 0 ]; then
    echo "  WARNING: 检测到死锁！" | tee -a "$MONITOR_LOG"
    jstack "$PID" 2>/dev/null | grep -A 30 "Found one Java-level deadlock" | tee -a "$MONITOR_LOG"
else
    echo "  无死锁" | tee -a "$MONITOR_LOG"
fi

# ---- 6. 关键指标汇总 ----
echo "" | tee -a "$MONITOR_LOG"
echo "[6] 关键指标汇总:" | tee -a "$MONITOR_LOG"
echo "-------------------------------------------" | tee -a "$MONITOR_LOG"

# 从 jstat 提取关键数据
GC_DATA=$(jstat -gcutil "$PID" 2>/dev/null | tail -1)
if [ -n "$GC_DATA" ]; then
    OLD_GEN=$(echo "$GC_DATA" | awk '{print $4}')
    YGC_COUNT=$(echo "$GC_DATA" | awk '{print $7}')
    YGC_TIME=$(echo "$GC_DATA" | awk '{print $8}')
    FGC_COUNT=$(echo "$GC_DATA" | awk '{print $9}')
    FGC_TIME=$(echo "$GC_DATA" | awk '{print $10}')

    echo "  老年代使用率: ${OLD_GEN}%" | tee -a "$MONITOR_LOG"
    echo "  Young GC 次数: $YGC_COUNT, 耗时: ${YGC_TIME}s" | tee -a "$MONITOR_LOG"
    echo "  Full GC 次数: $FGC_COUNT, 耗时: ${FGC_TIME}s" | tee -a "$MONITOR_LOG"

    # 告警：老年代使用率 > 80%
    OLD_GEN_INT=${OLD_GEN%.*}
    if [ "${OLD_GEN_INT:-0}" -gt 80 ]; then
        echo "  [ALERT] 老年代使用率超过 80%！建议检查内存泄漏或增大堆内存" | tee -a "$MONITOR_LOG"
    fi
fi

echo "" | tee -a "$MONITOR_LOG"
echo "监控完成，结果已保存到: $MONITOR_LOG"
