# CLAUDE.md — AI 材料申报与审核平台

> **项目定位**：面向高校竞赛、科研项目、奖学金等材料申报场景，提供申报通知上传、AI 结构化解析、材料清单生成、申请人材料提交、AI 完整性初审、教师/管理员人工复核、状态追踪与消息通知的一体化平台。

> **当前阶段**：在不推倒重写的前提下，将原"AI 竞赛材料管理系统"升级为更工程化的"AI 材料申报与审核平台"。**已完成**：状态模型重构（4 维度独立状态机）、搜索分页筛选、AI 解析草稿+人工确认流。**进行中**：补齐权限（JWT Filter + CurrentUser）、Redis 缓存、异步 AI 任务、接口文档、测试和部署能力。

---

## 1. 技术栈

### 后端
- **Java 21** + **Spring Boot 3.5.13**
- **MyBatis-Plus 3.5.7** — ORM，所有表逻辑删除（`@TableLogic`）
- **MySQL 8.0+** + **Flyway** — 数据库与版本迁移（禁止手动改表，必须通过 migration）
- **Spring Security** + **JJWT 0.12.6** — 认证与鉴权
- **Redis**（新增）— 缓存、防重复提交、分布式锁
- **Apache Tika 3.1.0** — 文件文本提取（DOCX/PDF/XLSX）
- **Apache PDFBox** — PDF 页面渲染，配合视觉模型 OCR
- **Knife4j / SpringDoc**（新增）— 接口文档
- **Lombok** — `@Data` on entities/DTOs，构造器注入（不用 `@Autowired`）

### 前端
- **Vue 3.5**（Composition API + `<script setup>`）
- **Vite 8** + **Element Plus 2.13**
- **Axios** — HTTP 请求（60s 超时）
- **SCSS** — 组件样式

### AI 能力
- **阿里云 DashScope**（OpenAI 兼容接口）
  - `qwen3.7-flash` — 通知解析、材料初审
  - `qwen3.7-flash` — PDF OCR 视觉识别（扫描件兜底）

### 部署
- **Docker** + **Docker Compose**（新增）— 一键启动 MySQL + Redis + 后端 + 前端

---

## 2. 后端分层规范

```
controller/   → 只做参数校验、调用 Service、返回 ApiResponse<T>
                禁止：直接操作 Mapper、写业务逻辑、手动解析 JWT（用 CurrentUser）
service/      → 业务逻辑、事务管理、状态校验、权限校验
                禁止：直接操作 HttpServletRequest、返回 JSON 字符串拼接
mapper/       → 只做数据访问，复杂 SQL 写在 src/main/resources/mapper/*.xml
                禁止：在 Java 代码里拼接复杂 SQL 字符串
entity/       → 纯 POJO，与数据库表一一对应，使用 @TableLogic
dto/          → 按功能分包：auth/notice/project/material/agent/ai/notify/user
                请求 DTO 必须加 @Valid 注解（@NotNull, @NotBlank 等）
common/       → 工具类：ApiResponse, FileTextExtractor, PdfOcrExtractor
config/       → SecurityConfig, LlmConfig, RedisConfig, AsyncConfig, Knife4jConfig
exception/    → BusinessException + GlobalExceptionHandler
util/         → JwtUtil, CurrentUser（新增）
```

### 2.1 统一响应格式

所有 Controller 返回 `ApiResponse<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-07-05T10:00:00"
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 / 业务异常 |
| 401 | 未登录 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 409 | 状态冲突 / 重复提交 |
| 500 | 系统异常 |
| 600 | AI 服务降级 |

### 2.2 获取当前用户

**必须使用 `CurrentUser` 工具类获取当前登录用户**，禁止在各 Controller 中手动解析 JWT。

```java
// ✅ 正确
SysUser currentUser = CurrentUser.get();

// ❌ 禁止
String token = request.getHeader("Authorization").substring(7);
Long userId = jwtUtil.getUserId(token);
```

### 2.3 事务边界

- `@Transactional` 加在 Service 层的写操作方法上
- Controller 层不加事务
- AI 调用方法本身不加事务（AI 失败不影响 DB 提交）

---

## 3. 前端开发规范

### 3.1 前端架构规范

- 前端使用 Vue 3 Composition API + Element Plus。
- 应用级页面导航使用 Vue Router 4。
- 左侧一级菜单必须对应独立路由页面，不使用滚动锚点或 `currentView + v-if` 承担应用级导航。
- `currentView`、`v-if`、Tab 仅用于同一页面内部的局部视图切换。
- 当前项目不引入 Vuex 或 Pinia。
- 少量跨页面共享状态使用模块级 `reactive()` 状态、composable 或 localStorage 管理。
- 认证信息统一由 `state/auth.js` 或等价认证模块维护，页面不得各自解析和维护 token。
- 路由守卫负责登录状态和角色页面访问体验；后端鉴权和资源归属校验是最终安全边界。

### 3.2 前端目录职责

- `views/`：路由级页面，一个文件对应一个主要页面。
- `components/`：可复用业务组件，不直接承担一级路由。
- `layouts/`：应用整体布局，包括侧栏、顶栏和 `<router-view />`。
- `router/`：路由定义和路由守卫。
- `config/menu.js`：角色菜单配置。
- `config/permissions.js`：前端动作权限常量和角色映射。
- `composables/`：可复用组合逻辑。
- `utils/status.js`：状态文案、标签样式和展示转换的唯一来源。
- 未经整体迁移决策，不混用 JavaScript 和 TypeScript。

### 3.3 前端权限与 API 规范

- 前端不得发送 `createdBy`、`uploadedBy`、`reviewerId` 等表示当前操作者身份的字段。
- 菜单隐藏和按钮隐藏只用于改善体验，不能替代后端鉴权。
- 页面操作必须同时判断角色权限、资源关系和业务状态。
- 401、403、404、409 和 AI 降级响应必须由 Axios 响应拦截器统一处理。
- 学生只能消费已发布通知；教师只能访问本人可审核或指导的项目；管理员访问管理页面。

---

## 4. 数据库变更规范

### 4.1 必须遵守

1. **所有表结构变更必须通过 Flyway migration**：在 `src/main/resources/db/migration/` 下新增 `V{序号}__{描述}.sql`。
2. **新增字段必须加 COMMENT**：说明字段用途和取值范围。
3. **新增字段必须有默认值**：确保兼容旧数据。NOT NULL 字段必须有 `DEFAULT`。
4. **禁止修改已有 migration 文件**：Flyway 校验 checksum，改了会导致启动失败。
5. **禁止在生产环境手动执行 SQL**。
6. **新增字段/表必须更新对应的 Entity 类**。

### 4.2 数据库连接

- 当前本地 Compose 连接由 `.env` 中的 `DB_USERNAME`、`DB_PASSWORD` 等变量提供；空密码不是可用的启动配置。
- 生产环境通过环境变量覆盖：`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- 连接 URL 必须包含 `useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai`

---

## 5. 权限与安全规则（⚠️ 最高优先级）

### 5.1 认证拦截

- **所有业务接口默认需要登录**。未登录 → 401。
- 放行白名单：`/auth/login`, `/auth/register`, `/doc.html`, `/v3/api-docs/**`, `/webjars/**`, 静态资源。
- 认证通过 `JwtAuthenticationFilter` 统一处理，写入 `SecurityContext`。
- **禁止在 Controller 中手动调用 `jwtUtil.parseToken()` 做认证**。

### 5.2 角色权限

| 接口 | 权限 |
|------|------|
| `/admin/**` | ADMIN |
| 通知上传、AI 解析确认、发布、归档 | ADMIN |
| 项目创建、材料上传 | STUDENT / ADMIN |
| 材料审核 | TEACHER / ADMIN |
| 消息查看 | 当前用户本人（或 ADMIN 查看全部） |
| 文件下载 | 项目成员 / 指导教师 / ADMIN |

### 5.3 资源归属校验（防止水平越权）

| 操作 | 校验规则 |
|------|---------|
| 查看项目详情 | 学生项目成员；或当前用户是该项目 advisor 且用户角色为 TEACHER；或 ADMIN |
| 添加/移除成员 | 当前用户是项目负责人 or ADMIN |
| 上传材料 | 当前用户是项目成员 or ADMIN |
| 审核材料 | 当前用户是该项目 advisor 且用户角色为 TEACHER；或 ADMIN。普通 TEACHER 不得审核未分配项目 |
| 查看消息 | `receiverId` = 当前用户 ID or ADMIN |
| 编辑个人资料 | `userId` = 当前用户 ID |

### 5.4 绝对禁止

1. ❌ **禁止用 `defaultValue` 给 `createdBy`/`uploadedBy`/`reviewerId` 等字段设默认用户 ID**。所有操作人必须从 `CurrentUser.get()` 获取。
2. ❌ **禁止只靠前端 `v-if` 隐藏按钮实现权限控制**。安全校验必须写在后端 Service 层。
3. ❌ **禁止在请求参数中传递当前用户 ID**（如 `?reviewerId=2`）。操作人身份只能从 JWT 中获取。
4. ❌ **禁止仓库中包含真实密码、API Key、数据库连接字符串等敏感信息**。全部通过环境变量配置。
5. ❌ **禁止在日志中打印密码、token、API Key**。

### 5.5 安全配置

- JWT Secret：生产环境通过 `JWT_SECRET` 环境变量设置，开发环境可用默认值
- DashScope API Key：通过 `DASHSCOPE_API_KEY` 环境变量设置
- 数据库密码：通过 `DB_PASSWORD` 环境变量设置
- CORS：允许前端开发服务器的跨域请求
- 提供 `.env.example` 模板文件，包含所有环境变量名和示例值（不含真实密钥）

---

## 6. AI 功能边界

### 6.1 核心原则

> **AI 生成草稿 / 初审意见 + 人工确认 / 复核 = 人机协同模式。AI 不直接替代人工决策。**

### 6.2 AI 能做什么

| 能力 | 模型 | 说明 |
|------|------|------|
| 通知文本结构化解析 | `qwen3.7-flash` | 提取标题/主办方/截止日期/面向对象/材料要求列表 |
| 材料内容完整性初审 | `qwen3.7-flash` | 返回 pass/warning/reject + 中文评审意见 |
| PDF 扫描件 OCR | `qwen3.7-flash` | Tika 提取失败时的视觉识别兜底 |

### 6.3 AI 不能做什么

- ❌ AI 解析结果**不能直接覆盖正式数据**。必须先写 `notice_parse_draft`，管理员确认后才发布。
- ❌ AI 材料检查结果**不能作为最终审核结论**。它是初审提示，最终必须由教师/管理员人工复核。
- ❌ AI 不能自动通过或拒绝申报项目。

### 6.4 AI 失败降级策略

| 场景 | 兜底行为 |
|------|---------|
| `DASHSCOPE_API_KEY` 未设置 | 记录 WARN 日志，返回中文降级提示，不影响系统运行 |
| LLM API 调用超时/失败 | catch 异常，返回 fallback 结果，不抛异常 |
| LLM 返回非 JSON | `cleanJson()` 提取 `{...}`，失败则 fallback |
| Tika 文本提取失败 + PDF | 自动切换 OCR 管线（PDFBox 渲染 + 视觉模型） |
| OCR 无结果或超过页数/字节/任务预算 | 明确标记不可用并要求提供可提取文本或人工核对；不得把未覆盖页视为完整成功 |

**关键原则**：AI 失败不准阻塞业务流程。所有 AI 调用必须有 try-catch + fallback。

---

## 7. 状态机设计原则

### 7.1 设计理念

将原混杂的 8 状态 `ProjectStatus` 拆分为 **4 个独立关注维度**，每个维度有自己的枚举和状态机：

| 维度 | 实现方式 | 是否状态机 | 说明 |
|------|---------|-----------|------|
| 项目生命周期 | `ProjectStatus`（DRAFT/UNDER_REVIEW/REVISION_REQUIRED/APPROVED） | ✅ | 实体领域方法驱动流转 |
| 通知解析进度 | `NoticeParseStatus`（DRAFT/PARSING/PARSED/FAILED） | ✅ | 支持人工录入和重新解析 |
| 通知发布阶段 | `NoticePublishStatus`（DRAFT/PUBLISHED/ARCHIVED） | ✅ | 简单的线性流转 |
| Agent 任务状态 | `AgentTaskStatus`（PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT） | ✅ | 终态判断 + 领域方法 |
| AI 检查结果 | `AiCheckResult`（PASSED/WARNING）+ `project_ai_check` 表 | ❌ 不可变 | 每次检查 INSERT 一行，不覆盖 |
| 教师审核决定 | `MaterialReviewDecision`（APPROVED/REVISION_REQUIRED）+ `material_review` 表 | ❌ 不可变 | 每次审核 INSERT 一行，不覆盖 |
| 材料完整度 | 实时计算 `requiredMaterials.allMatch(m -> m.currentVersionId != null)` | ❌ 派生 | 由 `refreshProjectProgress()` 计算 |
| 材料是否提交 | `currentVersionId != null` 推导 | ❌ 派生 | 提交 = 有当前版本 |

核心变更：**删除了 `MaterialSubmitStatus` 和 `MaterialReviewStatus` 两个枚举**，用表记录和派生属性替代。

### 7.2 项目生命周期 — ProjectStatus

```
DRAFT → UNDER_REVIEW ⇄ REVISION_REQUIRED → APPROVED
```

| 状态 | 含义 | 合法目标状态 |
|------|------|-------------|
| DRAFT | 草稿，材料未齐全 | UNDER_REVIEW |
| UNDER_REVIEW | 已提交，待教师审核 | APPROVED, REVISION_REQUIRED |
| REVISION_REQUIRED | 教师要求修改 | UNDER_REVIEW（重新提交） |
| APPROVED | 已通过 | 终态 |

领域方法（定义在 `CompetitionProject` 实体上）：
- `submitForReview()` — DRAFT → UNDER_REVIEW（前置条件：状态、通知已发布、未超期、材料齐全、是负责人）
- `approve()` — UNDER_REVIEW → APPROVED（乐观锁保护，并发冲突 → 409）
- `requestRevision()` — UNDER_REVIEW → REVISION_REQUIRED（乐观锁保护）
- `resubmit()` — REVISION_REQUIRED → UNDER_REVIEW

**乐观锁**：`CompetitionProject` 使用 `@Version Long version` 字段防止 approve/requestRevision 并发冲突。

### 7.3 通知解析进度 — NoticeParseStatus

```
DRAFT → PARSING → PARSED
              ↘ → FAILED → PARSED（人工录入）
              PARSED → PARSING（重新解析）
```

| 状态 | 含义 | 合法目标状态 |
|------|------|-------------|
| DRAFT | 已上传，待解析 | PARSING |
| PARSING | AI 解析中 | PARSED, FAILED |
| PARSED | 解析完成（替代旧名 CONFIRM_PENDING） | PUBLISHED（通过发布流程）, PARSING |
| FAILED | 解析失败（替代旧名 PARSE_FAILED） | PARSED（人工录入）, PARSING（重试） |

**注意**：CONFIRM（人工确认）逻辑已独立到 `confirmedAt`/`confirmedBy` 字段，不再是独立状态。

### 7.4 通知发布阶段 — NoticePublishStatus

```
DRAFT → PUBLISHED → ARCHIVED
```

| 状态 | 含义 |
|------|------|
| DRAFT | 未发布 |
| PUBLISHED | 已发布，学生可创建项目 |
| ARCHIVED | 已归档 |

**发布前置条件**：`parseStatus == PARSED` + `confirmedAt != null` + 材料清单存在。

### 7.5 Agent 任务状态 — AgentTaskStatus

```
PENDING → RUNNING → SUCCESS
                  → FAILED
                  → TIMEOUT
```

| 状态 | 含义 | isTerminal() |
|------|------|-------------|
| PENDING | 等待执行 | false |
| RUNNING | 执行中 | false |
| SUCCESS | 执行成功 | true |
| FAILED | 执行失败 | true |
| TIMEOUT | 执行超时 | true |

领域方法：`startRunning()`, `markSuccess()`, `markFailed()`, `markTimeout()`。终态不可再流转。

### 7.6 审核记录（不可变）

**AI 检查**：`AgentService.checkMaterial()` 写入 `project_ai_check` 表，记录 `AiCheckResult`（PASSED/WARNING）+ `material_snapshot`。每次检查 INSERT 一行，保留历史。

**教师审核**：`MaterialService.reviewMaterial()` 写入 `material_review` 表，记录 `MaterialReviewDecision`（APPROVED/REVISION_REQUIRED）。每次审核 INSERT 一行，不修改 `project_material`。

### 7.7 材料提交与完整度（派生）

- **是否已提交**：`currentVersionId != null`（不再用 `MaterialSubmitStatus` 枚举）
- **材料完整度**：`ProjectService.refreshProjectProgress()` 按 ACTIVE 必交材料要求及其当前已上传版本计算，并更新 `completionRate` 展示缓存；数据库存储过程/触发器仅兼容保留，不作为提交校验或页面完整度的权威。
- `ProjectService.refreshProjectProgress()` 只更新 `completionRate`，**不改 `project.status`**

### 7.8 必须遵守

1. **所有状态枚举统一风格**：`ALLOWED_TRANSITIONS` Map + `canTransitionTo()` + `validateTransitionTo()` + 领域方法（如 `publish()`/`archive()`）。
2. **非法流转 → `InvalidStatusTransitionException`**（HTTP 409），不再使用 `BusinessException` 处理状态流转错误。
3. **Controller 不直接修改状态**。状态变更通过实体领域方法（`entity.submitForReview()` 等）→ Service 层调用 → Mapper 持久化。
4. **状态枚举必须定义为常量或枚举类**，禁止在代码中散落魔法字符串。
5. **每次状态变更必须记录**：写入审核记录（`project_ai_check`/`material_review`）或通知消息（`notify_message`）。
6. **废弃的 `@Deprecated` 方法不得新增调用**：旧的 `static validateTransition(from, to)` 已删除，统一用实例方法 `validateTransitionTo(target)`。
7. **审核类操作使用不可变记录表**：不修改原记录的状态字段，每次操作 INSERT 一行新记录。

---

## 8. 接口设计规范

### 8.1 URL 命名

- RESTful 风格：资源名用复数名词
- 版本前缀（可选）：`/api`
- 示例：`GET /api/notices`, `POST /api/notices/{id}/publish`

### 8.2 请求与响应

- POST/PUT 请求体用 `@Valid @RequestBody`
- GET 请求参数用 `@RequestParam`
- 所有接口返回 `ApiResponse<T>`
- 分页接口统一参数：`pageNum`（默认 1）、`pageSize`（默认 10）

### 8.3 参数校验

- DTO 字段加 `@NotNull`, `@NotBlank`, `@Size`, `@Pattern` 等注解
- Controller 方法参数加 `@Valid`
- 校验失败由 `GlobalExceptionHandler` 统一返回 400

### 8.4 接口文档

- 所有 Controller 类加 `@Tag(name = "xxx")`
- 所有接口方法加 `@Operation(summary = "xxx")`
- DTO 字段加 `@Schema(description = "xxx")`
- 权限要求写在 `@Operation` 的 `description` 中

---

## 9. 异常处理规范

### 9.1 业务异常

```java
throw new BusinessException("用户可读的中文错误信息");
// → GlobalExceptionHandler → 400 + ApiResponse.fail(400, message)
```

### 9.2 全局异常处理

| 异常类型 | HTTP 状态码 | 说明 |
|---------|-----------|------|
| `BusinessException` | 400 | 业务规则违反 |
| `InvalidStatusTransitionException` | 409 | 非法状态流转 |
| `MethodArgumentNotValidException` | 400 | 参数校验失败 |
| `AccessDeniedException` | 403 | 权限不足 |
| `OptimisticLockException` | 409 | 乐观锁并发冲突（状态已变更，请刷新重试） |
| `Exception`（其他） | 500 | 系统异常 |

### 9.3 日志规范

- Service 层关键操作：`log.info("操作描述: keyParam={}", value)`
- AI 调用：`log.info("LLM {} 调用开始: input length={}", toolName, length)`
- 异常：`log.error("操作失败: {}", e.getMessage(), e)` — 但**不打印密码/token/API Key**

---

## 10. 禁止事项（总清单）

| 编号 | 禁止行为 | 说明 |
|------|---------|------|
| 1 | 绕过 Spring Security 认证 | 所有业务接口必须通过 JwtAuthenticationFilter |
| 2 | 使用默认 userId 代替当前用户 | `createdBy`/`uploadedBy`/`reviewerId` 只能从 CurrentUser 获取 |
| 3 | 只在前端隐藏按钮做权限 | 后端必须有角色和资源归属校验 |
| 4 | AI 结果直接覆盖正式数据 | 必须先写草稿表，管理员确认后才发布 |
| 5 | 不校验状态流转合法性 | 状态变更前必须检查当前状态 → 目标状态是否合法 |
| 6 | 删除已有关联项目的材料要求 | 已发布 + 已有项目的通知，只能追加/停用，不能删除 |
| 7 | Controller 中写业务逻辑 | Controller 只做参数校验 + 调用 Service + 返回结果 |
| 8 | 手动解析 JWT | 必须用 `CurrentUser` 工具类 |
| 9 | 直接拼接 SQL 字符串 | 复杂查询写在 mapper XML 中 |
| 10 | 修改已有 Flyway migration | 新增 migration 文件，不修改旧的 |
| 11 | 新增无默认值的 NOT NULL 字段 | 必须设 DEFAULT 值 |
| 12 | 硬编码敏感信息 | 密钥/密码/API Key 通过环境变量 |
| 13 | 日志中打印密码/token | 敏感信息脱敏 |
| 14 | 新增接口无参数校验 | 必须加 `@Valid` + JSR 注解 |
| 15 | 新增接口无异常处理 | 异常由 GlobalExceptionHandler 统一处理 |
| 16 | 推倒重写 | 在现有代码基础上改造，保留已有可用功能 |
| 17 | 引入微服务/消息队列/流程引擎 | 项目定位为单体应用，不做过度设计 |

---

## 11. 每次开发前必须检查的事项

开发任何功能前，先走以下检查清单：

1. **阅读相关文件**
   - 涉及到的 Controller、Service、Mapper、Entity
   - 前端对应的组件和 API 调用
   - 相关的 Flyway migration 文件和数据库表结构

2. **确认影响范围**
   - 改了这张表，哪些 Entity/DTO/Mapper 需要同步更新？
   - 改了这个接口，哪些前端组件在调用它？
   - 改了这个 Service 方法，哪些 Controller 在调用它？

3. **输出 Plan 并获得确认**
   - 描述要做什么、改哪些文件、每个文件改什么
   - 确认后再开始写代码
   - **不直接大规模改代码**

4. **考虑兼容性**
   - 新字段对旧数据是否兼容？（必须有默认值）
   - 新接口是否会影响已有接口？
   - 前端 Mock 服务是否需要同步更新？

---

## 12. 每次开发完成后的验收清单

### 12.1 功能验收

- [ ] 功能按预期工作
- [ ] 参数校验生效（传非法参数能收到 400）
- [ ] 异常处理正确（异常场景能收到合理的错误信息）
- [ ] 前端 Mock 模式仍可用（如果 `VITE_USE_MOCK=true`）

### 12.2 权限验收

- [ ] 未登录访问返回 401
- [ ] 低权限用户访问高权限接口返回 403
- [ ] 非项目成员不能操作他人项目
- [ ] 操作用户身份来自 JWT，不是请求参数

### 12.3 数据库验收

- [ ] 新增字段有 COMMENT 注释
- [ ] 新增 NOT NULL 字段有 DEFAULT 值
- [ ] Flyway migration 文件命名规范（`V{序号}__{描述}.sql`）
- [ ] 未修改已有 migration 文件

### 12.4 状态机验收

- [ ] 合法状态流转可以通过
- [ ] 非法状态流转被拦截并返回明确错误信息
- [ ] 状态变更写入了审核记录或通知消息

### 12.5 AI 相关验收

- [ ] AI 结果先写草稿，不直接覆盖正式数据
- [ ] AI 失败有 fallback，不抛异常、不阻塞业务流程
- [ ] AI 任务日志有记录（`agent_task_log`）

### 12.6 代码质量验收

- [ ] 没有硬编码的 userId/password/token/API Key
- [ ] 没有在 Controller 中手动解析 JWT
- [ ] 没有在 Controller 中直接操作 Mapper
- [ ] 没有魔法字符串散落（状态用枚举/常量）
- [ ] DTO 有 `@Valid` 注解
- [ ] 关键操作有日志

---

## 13. 当前代码已知问题（待改造）

以下问题在改造计划中已识别，开发时注意不要沿用：

| 问题 | 影响 | 状态 |
|------|------|------|
| `SecurityConfig` 有 `anyRequest().permitAll()` | 所有接口放行 | ❌ 待改造 (P0-1) |
| Controller 手动解析 JWT 不统一 | 重复代码，易出错 | ❌ 待改造 (P0-1) |
| `NoticeController.createdBy` 默认值 `"1"` | 身份伪造 | ❌ 待改造 (P0-2) |
| `MaterialController.uploadedBy` 默认值 `"3"` | 身份伪造 | ❌ 待改造 (P0-2) |
| `MaterialReviewRequest.reviewerId` 来自请求体 | 可伪造审核人 | ❌ 待改造 (P0-2) |
| `AdminController` 无权限校验 | 任何人可管理用户 | ❌ 待改造 (P0-3) |
| `parseNotice()` 删除旧 `material_requirement` 再重建 | 已有项目数据不一致 | ❌ 待改造 (P1-3) |
| 没有 Redis | 无缓存/防重复 | ❌ 待改造 (P1-7) |
| AI 调用同步阻塞 | 用户体验差 | ❌ 待改造 (P1-8) |
| 没有接口文档 | 面试官无法理解 API | ❌ 待改造 (P1-9) |
| 没有定时任务 | 无截止提醒 | ❌ 待改造 (P1-10) |
| 只有一个默认测试类 | 无测试覆盖 | ❌ 待改造 (P1-11) |
| `sql.py` 含真实数据库密码 | 安全泄露 | ❌ 待改造 (P0-4) |

### 13.1 已完成的改造

| 项目 | 说明 |
|------|------|
| ✅ P1-1 解析草稿 + 人工确认 | AI 解析结果写入 `notice_parse_draft`，管理员确认后发布 |
| ✅ P1-2 状态机重构 | 4 维度独立状态机（ProjectStatus/NoticeParseStatus/NoticePublishStatus/AgentTaskStatus），删除 MaterialSubmitStatus/MaterialReviewStatus |
| ✅ P1-4 搜索分页筛选 | 统一为所有列表端点补齐查询能力 |
| ✅ 状态机测试 | `StatusStateMachineTests` — 40 个测试覆盖 4 个状态机 |

### 13.2 状态模型重构后已知遗留问题

| 问题 | 影响 | 建议 |
|------|------|------|
| `NoticeService.confirmParse()` Javadoc 旧状态名残留 | 文档不准确 | 确认后批量替换 |
| `ProjectService` 编译警告（引用 @Deprecated 字段） | 无功能影响 | 等 DB 旧列 DROP 后自然消除 |
| `MaterialService.resetMaterialReview()` @Deprecated 仍有调用者 | 无功能影响 | 保留到后续迭代删除 |
| `CompetitionNotice.completeManualEntry()` / `restartParsing()` 无 Service 调用者 | 枚举合法路径未接入 Controller | Controller 端点待后续接入 |
| `AgentTaskLog` 领域方法（startRunning/markSuccess 等）无调用者 | 异步框架未接入 | @Async 异步任务框架接入时使用 |
| Flyway 版本号跳跃 V2→V7 | V3-V6 缺失 | V7 使用 IF NOT EXISTS 保证幂等 |
| `project_material` 旧列未 DROP（submit_status, review_status 等） | 冗余字段 | 确认不再读取后新增 migration 删除 |

---

## 14. 环境变量参考

开发时需要配置的环境变量（提供 `.env.example` 模板）：

```bash
# 数据库（开发环境默认值）
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ai_competition_db
DB_USERNAME=root
DB_PASSWORD=<set-in-local-env>
DB_SSL_MODE=DISABLED

# JWT
JWT_SECRET=<set-a-unique-secret-in-your-local-environment>

# AI（可选，不设置则 AI 降级运行）
DASHSCOPE_API_KEY=

# Redis（开发环境默认值）
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# 服务端口
PORT=8080
```

---

## 15. 快速启动

```bash
# 方式一：Docker Compose（推荐）
docker-compose up

# 方式二：手动启动
# 1. 启动 MySQL + Redis
# 2. 后端
cd backend
./mvnw spring-boot:run
# 3. 前端
cd frontend
npm install
npm run dev
```

验证：
- 后端 API：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`
- 前端页面：`http://localhost:5173`

---

> **核心原则**：安全优先、AI 辅助不替代人工、改动前先给 plan、改动后验证权限和状态。
