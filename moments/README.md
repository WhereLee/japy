# japy-moments · 标准 QQ空间/朋友圈式动态社区

一个干净、标准的"动态社区"Java 后端：用户发动态（说说）、点赞（带赞列表）、评论（楼中楼）、个人主页、通知、举报、管理后台。**与小说/RAG 完全解耦**，作为独立子项目存在。

## 技术栈

| 项 | 值 |
|---|---|
| 框架 | Spring Boot 3.2.5 + Java 17 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | PostgreSQL（库名 `japy_moments`） |
| 认证 | JWT（jjwt 0.12.5）+ BCrypt |
| 端口 | 8083 |

## 快速开始

```bash
# 1. 建库（只需一次）
psql -U postgres -c "CREATE DATABASE japy_moments;"
# 或任意客户端执行：CREATE DATABASE japy_moments;

# 2. 修改 application.yml 中的数据库账号密码

# 3. 启动（schema.sql 会自动建表 + 预置 admin 账号 admin/admin123）
mvn spring-boot:run
```

## 功能清单

**用户端**
- 注册/登录（JWT，7天有效）
- 发动态 / 删自己的动态（敏感词拦截）
- 全局时间线（公开可看，置顶优先 + 最新在前）
- 点赞/取消点赞，赞列表（谁赞了）
- 评论 + 楼中楼回复（一层嵌套，回复通知被回复人）
- 个人主页（TA的动态分页）
- 通知（被赞/被评论/被回复/被隐藏/举报结果/公告，未读数 + 全部已读）
- 举报动态/评论（禁止举报自己、禁止重复）
- 个人资料/改密码/我的动态/我的评论

**管理端（/api/admin/**，需 admin 角色）**
- Dashboard 统计
- 用户管理：封禁/解封/重置密码/强制改名
- 动态管理：隐藏/恢复/删除/置顶/取消置顶
- 评论管理：隐藏/恢复/删除
- 举报处理：通过（自动隐藏内容 + 通知举报者）/ 驳回
- 敏感词管理（内存缓存 60s TTL）
- 公告广播 / 操作日志

## 核心设计

- **软删除**：动态/评论用 status 标记（0正常/1隐藏/2删除），不物理删除
- **点赞唯一约束**：`UNIQUE (moment_id, user_id)`，点赞幂等
- **楼中楼**：评论表 `parent_id` 指向顶层评论，只允许一层嵌套（回复不能再回复），列表接口一次查出顶层分页 + 全部子回复（无 N+1）
- **点赞状态批量填充**：时间线一次 `IN` 查询填充当前用户 liked 状态，无 N+1
- **评论计数**：删除评论时实时重算该动态的可见评论数，保证一致
- **昵称快照**：动态/点赞/评论保存发布时昵称快照；拦截器每次请求从库中取最新昵称，强改昵称立即生效

## 数据库表

`app_user` 用户 · `moment` 动态 · `moment_like` 点赞 · `comment` 评论(楼中楼) · `notification` 通知 · `report` 举报 · `sensitive_word` 敏感词 · `operation_log` 操作日志

## 测试（JUnit 集成测试，109 项）

```bash
mvn test   # 需要 PostgreSQL japy_moments 库已存在（schema 由应用启动时创建）
```

| 测试类 | 覆盖 | 数量 |
|---|---|---|
| AuthTest | 注册/登录正常路径 + 边界（长度/空值/重复/非法JSON/伪造token） | 17 |
| MomentTest | 动态发布/时间线/点赞/赞列表 + 敏感词/置顶/隐藏/删除归属 | 26 |
| CommentTest | 楼中楼回复/非法父评论/跨动态回复拦截/计数一致性/删除级联 | 17 |
| ProfileNotificationTest | 公开主页/资料/密码/通知已读链路/自赞自评不通知 | 20 |
| ReportAdminTest | 举报边界/封禁解封/重置密码/强改昵称/举报处理/敏感词即时生效/公告/日志 | 27 |
| EdgeCaseTest | P0/P1 回归：孤儿数据/计数一致/size上限/通知节流/点赞语义/子回复分页/游标分页 | 11 |
| ConcurrencyTest | 同一用户并发点赞（唯一约束幂等）、不同用户并发点赞（计数正确） | 2 |

**测试要点：**
- 全部通过 MockMvc 走真实 HTTP 层（含拦截器/权限/JWT 全链路）
- 用户名带类名前缀 + 时间戳，测试可重复运行（已连续验证两次全绿）
- 中文断言：响应解析必须显式 UTF-8（`getContentAsString(StandardCharsets.UTF_8)`），否则中文乱码
- 并发测试曾暴露真实缺陷：点赞计数读-改-写丢失更新（5线程并发后 likeCount=1）→ 已修复为原子 SQL（`like_count = like_count + 1`），并补 `DuplicateKeyException` 幂等处理

## 冒烟测试

```bash
python smoke_test.py   # 27 项快速冒烟：注册→发动态→点赞→楼中楼→通知→主页→删除归属
```

## API 速览

```
POST /auth/register  POST /auth/login
GET  /api/moments           时间线（公开；支持 cursor 游标分页，格式 "createdAt_id"）
POST /api/moments           发动态
DELETE /api/moments/{id}    删自己的动态（级联软删其评论）
POST /api/moments/{id}/like 点赞/取消（动态不存在/被隐藏时明确返回 400）
GET  /api/moments/{id}/likes 赞列表
GET  /api/comments?momentId=&replySize=  评论列表（顶层分页 + 子回复取最新 replySize 条）
POST /api/comments           评论/回复（parentId 可选）
DELETE /api/comments/{id}    删自己的评论（顶层评论的子回复一并删除）
GET  /api/users/{id}         公开主页（含动态分页）
GET/PUT /api/users/me*       我的资料/动态/评论/密码
GET/PUT /api/notifications*  通知
POST /api/reports            举报
GET  /api/admin/**           管理端
```

## 经典社区系统经验教训落地（调研结论）

对照微信朋友圈/微博/Twitter/Reddit 等公开经验审计后修复：

**P0（数据一致性/安全，已修+已测）**
- 删除动态后其评论/点赞成为可查孤儿数据 → 删除时**级联软删评论**，评论/赞列表接口校验动态状态
- 评论计数与可见评论不一致（隐藏/删除评论时计数不更新）→ 隐藏/恢复/删除均**原子同步计数**
- 分页 size 无上限可拉全表 → 所有分页接口统一 `PageParams` 规整（1~100）
- 敏感词缓存失效与读取并发竞态（删词后旧词可能继续拦截）→ `synchronized` 同步

**P1（体验/健壮性，已修+已测）**
- 反复"点赞-取消-点赞"刷爆通知 → 点赞通知**节流**（同动态存在未读点赞通知则不重复发）
- 点赞接口 `liked=false` 语义模糊（取消 vs 动态不存在）→ 动态不存在明确 400
- 楼中楼子回复无限返回 → `replySize` 分页（默认取最新 20 条）
- offset 翻页在滚动时重复/遗漏 → 时间线**游标分页**（`(created_at, id)` 行值比较，`cursor="createdAt_id"`）

**P2（已知边界，当前不做）**
- 接口限流、Flyway schema 版本管理、JWT 密钥/DB 密码环境变量化、统一 UTC 时区
- XSS 防护依赖前端渲染转义（后端原样存储是设计选择）

