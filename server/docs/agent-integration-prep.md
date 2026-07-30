# Agent 接入开闭原则改造备忘

> 本文档记录 Agent 模块接入前需要改造的扩展点，接入时按需执行即可。

## 一、天然扩展点（无需改动）

| 扩展点 | 机制 | Agent 接入方式 |
|--------|------|---------------|
| 批注类型处理 | `AnnotationTypeHandlerFactory`（Spring 自动注入） | 新增 `AiModerationHandler implements AnnotationTypeHandler` |
| 业务指标 | `BusinessMetrics`（Micrometer） | 新增 Counter/Timer |
| 健康检查 | `NovelStorageHealthIndicator`（Spring Boot） | 新增 `HealthIndicator` Bean |

## 二、需改造的硬编码点

### O1：AnnotationService 缺少事件钩子

**现状**：`AnnotationService.create()` / `delete()` 直接内联执行，无事件发布。

**改造方案**：引入 Spring Event
- 创建 `AnnotationCreatedEvent` / `AnnotationDeletedEvent`
- `AnnotationService` 中 `applicationEventPublisher.publishEvent(...)`
- Agent 监听事件执行审核，无需修改 Service 代码

**改动文件**：
- 新建 `event/AnnotationCreatedEvent.java`
- 新建 `event/AnnotationDeletedEvent.java`
- 修改 `AnnotationService.java`（注入 ApplicationEventPublisher，发布事件）
- Agent 侧新建 `listener/AiContentModerationListener.java`

### O2：Notification 类型硬编码

**现状**：`NotificationService.send()` 的 type 参数为 String，类型散落各处。

**改造方案**：枚举化 + 支持动态注册
- 创建 `NotificationType` 枚举（like/comment/reply/system/ai_moderation）
- Agent 审核结果通知新增 `AI_MODERATION` 类型
- 可选：支持运行时注册自定义通知类型

**改动文件**：
- 新建 `enums/NotificationType.java`
- 修改 `NotificationService.java`（String → 枚举）
- 修改 `Notification.java` entity（type 字段注释更新）

### O3：批注后处理无扩展链

**现状**：`AnnotationController` 直调 `AnnotationService`，无后处理拦截。

**改造方案**：`AnnotationPostProcessor` 接口
- 类似 Spring 的 `BeanPostProcessor` 思维
- 批注创建/删除后经过后处理链（审核、统计、推荐等）
- Agent 实现 `AnnotationPostProcessor` 即可接入

**改动文件**：
- 新建 `strategy/AnnotationPostProcessor.java`（接口）
- 新建 `strategy/DefaultAnnotationPostProcessor.java`（默认空实现）
- 修改 `AnnotationService.java`（注入 List<AnnotationPostProcessor>，创建后遍历调用）

## 三、推荐执行顺序

接入 Agent 时按以下顺序改造：
1. O1（Spring Event）— 最核心，解耦事件驱动
2. O2（通知枚举）— 简单，顺手做了
3. O3（后处理链）— 如果需要更复杂的后处理逻辑再做
