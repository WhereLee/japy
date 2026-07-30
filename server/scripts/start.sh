#!/bin/bash
# ============================================================
# ReCloud 启动脚本 — JVM 调优参数配置
# ============================================================
#
# JVM 参数选择依据：
# 1. 堆内存 -Xms256m -Xmx512m
#    - 项目为中小型 Web 应用，512MB 足够支撑 200+ 并发
#    - Xms=Xmx 避免运行时堆扩缩带来的 GC 停顿
#
# 2. G1 垃圾收集器 (-XX:+UseG1GC)
#    - 相比 CMS：G1 可预测停顿时间、无浮动垃圾问题、支持大堆
#    - 相比 ZGC：G1 在 JDK17 上更成熟，吞吐量更优
#    - 适合本项目 < 1GB 堆的场景
#
# 3. MaxGCPauseMillis=200
#    - 目标停顿 200ms，兼顾响应时间和吞吐量
#    - 对于 Web 应用，200ms 内用户无感知
#
# 4. HeapDumpOnOutOfMemoryError
#    - OOM 时自动 dump，便于事后分析内存泄漏
#
# 5. GC 日志
#    - JDK17 使用统一 JVM 日志 (-Xlog:gc*) 替代旧版 PrintGCDetails
#    - 输出到文件，可用 GCViewer 等工具分析
#
# 6. 元空间 -XX:MaxMetaspaceSize=256m
#    - 防止类加载过多导致本地内存溢出
#    - 256MB 对 Spring Boot 项目足够
#
# 7. GC 日志轮转 -Xlog:gc*:file=...:time,tags:filecount=5,filesize=10M
#    - 最多5个文件，每个10MB，自动轮转
# ============================================================

APP_NAME="recloud"
JAR_FILE="$(dirname "$0")/../target/recloud-1.0.0.jar"
LOG_DIR="$(dirname "$0")/../logs"

# 创建日志目录
mkdir -p "$LOG_DIR"

# JVM 内存参数
JVM_MEM_OPTS="-Xms256m -Xmx512m -XX:MaxMetaspaceSize=256m"
# 直接内存限制：Lettuce/Netty 使用直接内存，不限制可能导致本地内存溢出
JVM_MEM_OPTS="$JVM_MEM_OPTS -XX:MaxDirectMemorySize=128m"

# GC 参数（G1 收集器）
JVM_GC_OPTS="-XX:+UseG1GC"
JVM_GC_OPTS="$JVM_GC_OPTS -XX:MaxGCPauseMillis=200"
JVM_GC_OPTS="$JVM_GC_OPTS -XX:G1HeapRegionSize=4m"
JVM_GC_OPTS="$JVM_GC_OPTS -XX:InitiatingHeapOccupancyPercent=45"

# GC 日志（JDK17 统一日志格式）
JVM_GC_LOG_OPTS="-Xlog:gc*:file=${LOG_DIR}/gc.log:time,tags:filecount=5,filesize=10M"

# OOM 诊断
JVM_DIAG_OPTS="-XX:+HeapDumpOnOutOfMemoryError"
JVM_DIAG_OPTS="$JVM_DIAG_OPTS -XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof"

# 编码
JVM_MISC_OPTS="-Dfile.encoding=UTF-8"
JVM_MISC_OPTS="$JVM_MISC_OPTS -Djava.security.egd=/dev/./urandom"

# 组装所有 JVM 参数
JVM_OPTS="$JVM_MEM_OPTS $JVM_GC_OPTS $JVM_GC_LOG_OPTS $JVM_DIAG_OPTS $JVM_MISC_OPTS"

echo "============================================"
echo "  Starting $APP_NAME"
echo "  JVM Options: $JVM_OPTS"
echo "============================================"

# 启动应用
exec java $JVM_OPTS -jar "$JAR_FILE" \
    --spring.profiles.active=prod \
    "$@"
