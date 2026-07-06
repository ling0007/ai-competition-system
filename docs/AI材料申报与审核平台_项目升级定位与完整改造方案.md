# AI 材料申报与审核平台：项目升级定位与完整改造方案

> 基于现有项目《AI 竞赛材料管理系统》审查结果制定。  
> 改造目标：不推倒重写，在保留现有“通知上传 → AI 解析 → 项目创建 → 材料提交 → AI 检查 → 教师审核 → 消息通知”链路的基础上，将项目升级为更通用、更适合 Java 后端 / AI 应用实习面试的工程化项目。

---

## 1. 最终定位结论

### 1.1 推荐项目名称

**AI 材料申报与审核平台**

### 1.2 不建议使用的名称

| 名称 | 是否建议 | 原因 |
|---|---:|---|
| AI 竞赛材料管理系统 | 不建议作为最终名称 | 场景偏窄，容易像课程设计 |
| AI 材料流转审批平台 | 可作为副标题 | “流转审批”可以体现工程性，但如果没有多级审批配置，主标题略大 |
| 企业级智能材料审批系统 | 不建议 | 当前系统还未达到企业级、多租户、流程引擎级别 |
| 智能 OA 审批平台 | 不建议 | OA 范围过大，容易被追问请假、报销、流程编排等未实现能力 |
| 高校项目申报与材料审核平台 | 可选 | 更稳，但通用性略弱 |

### 1.3 一句话定位

> 面向高校竞赛、科研项目、奖学金、企业资质等材料申报场景，提供申报通知上传、AI 结构化解析、材料清单生成、申请人材料提交、AI 初审、人工复核、状态追踪与消息通知的一体化平台。

### 1.4 简历版项目描述

> 基于 Spring Boot + Vue3 设计并实现 AI 材料申报与审核平台，支持申报通知上传、PDF/DOCX 文本提取、AI 结构化解析、材料清单生成、项目申报、材料版本提交、AI 完整性初审、教师/管理员复核与消息通知。系统通过 JWT 鉴权、角色权限、状态流转、材料版本管理和 AI 降级策略，解决材料申报过程中要求分散、人工初审重复、材料漏交和审核过程不可追踪等问题。

### 1.5 面试中应避免的夸张表述

不要说：

- “这是一个企业级 OA 系统”
- “这是一个完整的审批流引擎”
- “AI 可以自动完成材料审核”
- “系统可以替代人工审核”
- “已经达到生产级安全标准”
- “支持所有类型文档和复杂流程配置”

应该说：

- “这是一个面向材料申报场景的轻量审核平台”
- “AI 负责结构化解析和初审提示，最终审核由人工完成”
- “审批流程目前是固定流程，不是通用流程引擎”
- “系统重点展示了后端权限、状态流转、文件解析、AI 降级和材料版本管理”

---

## 2. 为什么这样改定位

### 2.1 现有项目已有的真实能力

当前项目已经实现：

- 用户注册 / 登录 / JWT 生成
- 管理员用户管理
- 通知文件上传
- AI 解析通知
- 基于通知创建项目
- 项目成员管理
- 材料逐项上传和版本递增
- 项目完成率计算
- AI 材料完整性检查
- 教师审核材料
- 通知消息系统
- AI 任务日志
- 文件下载和 PDF 预览
- PDF 文本提取失败后的视觉 OCR 兜底
- 前端 Mock 模式

这些能力的本质不是“竞赛”，而是：

**材料要求生成 + 材料提交 + AI 初审 + 人工复核 + 状态追踪。**

因此，项目可以自然升级为更通用的材料申报审核平台。

### 2.2 原定位的问题

“竞赛材料管理系统”的问题：

1. 场景窄，容易被看作校园课设。
2. 管理员目前只管理用户，没有真正管理申报流程。
3. AI 解析直接落库，缺少人工确认，不符合真实业务。
4. 权限控制不完整，面试时容易被质疑。
5. 状态流转不够清楚，无法体现后端工程设计能力。

### 2.3 新定位的优势

“AI 材料申报与审核平台”的优势：

1. 场景更通用：竞赛、科研项目、奖学金、资质材料、合同附件都能解释。
2. 和现有代码匹配：不需要推倒重写。
3. 更适合 Java 后端面试：权限、状态机、事务、文件、消息、审核记录都能讲。
4. AI 使用更自然：AI 做辅助解析和初审，而不是硬加大模型。
5. 边界更诚实：不是完整 OA，也不是企业级流程引擎。

---

## 3. 改造总原则

### 3.1 不做什么

1. 不推倒重写。
2. 不引入微服务。
3. 不做复杂流程引擎。
4. 不做多租户。
5. 不做低代码表单。
6. 不做自动代替人工审批。
7. 不为了炫技引入大量中间件。
8. 不把功能堆成大而散的系统。

### 3.2 要做什么

1. 把权限做真实。
2. 把 AI 结果变成“待确认草稿”，而不是直接改正式数据。
3. 把通知、项目、材料状态流转做清楚。
4. 把管理员从“只管账号”扩展为“管理申报通知和 AI 解析结果”。
5. 把搜索、分页、筛选补齐。
6. 把材料版本、审核记录、消息通知讲成完整闭环。
7. 把 README、演示流程、测试账号、接口文档整理好。
8. 把已有亮点保留：OCR 管线、材料版本、完成率计算、AI 降级、Mock 模式。

---

## 4. 目标业务链路

### 4.1 改造后的主链路

```text
管理员上传申报通知
→ 系统提取通知文本
→ AI 解析通知，生成材料要求草稿
→ 管理员确认 / 修改解析结果
→ 发布申报任务
→ 申请人创建申报项目
→ 系统根据材料要求生成材料清单
→ 申请人逐项上传材料
→ 系统记录材料版本并刷新完成率
→ 申请人发起 AI 初审
→ AI 给出完整性检查和风险提示
→ 教师 / 审核人进行人工复核
→ 通过 / 退回修改
→ 系统记录审核意见、消息通知和状态变化
```

### 4.2 角色设计

| 角色 | 改造后职责 |
|---|---|
| 申请人 / 学生 | 查看已发布申报通知，创建申报项目，管理成员，上传材料，查看 AI 初审和人工审核意见 |
| 教师 / 审核人 | 查看自己负责的项目，预览材料，审核材料，填写修改意见 |
| 管理员 | 上传申报通知，触发 AI 解析，确认材料要求，发布申报任务，查看项目状态，管理用户 |
| 系统 / AI | 文本提取、通知解析、材料完整性初审、失败降级、日志记录 |

---

## 5. 完整改造清单

## P0：必须改，否则不适合当主项目

### P0-1. 真实 JWT 鉴权与统一用户身份

| 项目 | 内容 |
|---|---|
| 当前问题 | Spring Security 配置为 `anyRequest().permitAll()`，JWT 没有真正进入认证链 |
| 改造目标 | 所有业务接口默认需要登录，登录/注册/健康检查放行 |
| 涉及模块 | `SecurityConfig`, `JwtUtil`, 新增 `JwtAuthenticationFilter`, 统一异常处理 |
| 面试价值 | 能讲清楚认证、鉴权、SecurityContext、401/403 区别 |
| 难度 | 中 |

具体改造：

1. 新增 `JwtAuthenticationFilter`。
2. 从 `Authorization: Bearer <token>` 解析用户。
3. 校验 token 有效后写入 `SecurityContextHolder`。
4. 新增 `CurrentUser` 工具类或注解，统一获取当前用户。
5. 登录、注册、静态资源、健康检查放行。
6. 其他接口默认认证。
7. 未登录返回 401。
8. 权限不足返回 403。

建议权限策略：

| 接口类型 | 权限 |
|---|---|
| `/auth/login`, `/auth/register` | 放行 |
| `/admin/**` | ADMIN |
| 通知上传、AI 解析确认、发布 | ADMIN |
| 项目创建、材料上传 | STUDENT / ADMIN |
| 材料审核 | TEACHER / ADMIN |
| 消息查看 | 当前用户本人 |
| 文件下载 | 项目成员 / 教师 / 管理员 |

---

### P0-2. 去掉默认 userId 和请求参数伪造身份

| 项目 | 内容 |
|---|---|
| 当前问题 | `createdBy=1`, `uploadedBy=3`, `reviewerId` 可由请求方控制 |
| 改造目标 | 所有操作人都从当前登录用户获得 |
| 涉及模块 | `NoticeController`, `MaterialController`, `ProjectController`, `AgentController`, 前端 service |
| 面试价值 | 能体现安全意识和越权防护 |
| 难度 | 中 |

具体改造：

1. 通知上传的 `createdBy` 从当前用户获取。
2. 材料上传的 `uploadedBy` 从当前用户获取。
3. 教师审核的 `reviewerId` 从当前用户获取。
4. 前端删除 `createdBy`、`uploadedBy`、`reviewerId` 参数。
5. Service 层统一校验当前用户是否有权操作对应资源。
6. 保留 `userId` 参数只用于管理员管理用户，不用于表示当前操作者。

---

### P0-3. 后端角色权限与项目归属校验

| 项目 | 内容 |
|---|---|
| 当前问题 | 前端切换角色视图，但后端不真正限制接口 |
| 改造目标 | 所有关键接口在后端校验角色和资源归属 |
| 涉及模块 | `ProjectService`, `MaterialService`, `NotifyService`, `AdminController` |
| 面试价值 | 垂直越权、水平越权都能讲清楚 |
| 难度 | 中 |

校验规则：

| 场景 | 校验 |
|---|---|
| 查看项目详情 | 当前用户是项目成员、指导教师或管理员 |
| 添加成员 | 当前用户是项目负责人或管理员 |
| 移除成员 | 当前用户是项目负责人或管理员，且不能移除负责人本人 |
| 上传材料 | 当前用户是项目成员或管理员 |
| 审核材料 | 当前用户是项目指导教师、教师角色或管理员 |
| 查看消息 | 只能查看自己的消息，管理员可查看全部 |
| 管理用户 | 仅管理员 |

---

### P0-4. 清理敏感信息和环境变量

| 项目 | 内容 |
|---|---|
| 当前问题 | `sql.py` 中存在数据库密码泄露风险 |
| 改造目标 | 所有密钥通过环境变量配置，仓库不保留真实密码 |
| 涉及模块 | `application.yml/properties`, `sql.py`, README |
| 面试价值 | 体现基本安全意识 |
| 难度 | 低 |

具体改造：

1. 删除仓库中的真实数据库密码。
2. 数据库账号、密码、AI Key、JWT Secret 全部使用环境变量。
3. 提供 `.env.example`。
4. 说明旧密钥已废弃。
5. README 中只给示例配置，不给真实值。

---

### P0-5. 管理员业务功能补齐

| 项目 | 内容 |
|---|---|
| 当前问题 | 管理员只能管理用户，不能管理通知、解析结果和申报任务 |
| 改造目标 | 管理员成为申报通知和 AI 解析确认的管理者 |
| 涉及模块 | `AdminDashboard`, `NoticeUploadPanel`, `NoticeService`, 前端路由 |
| 面试价值 | 让角色设计和业务逻辑一致 |
| 难度 | 中 |

管理员应新增能力：

1. 上传申报通知。
2. 触发 AI 解析。
3. 查看 AI 解析草稿。
4. 修改材料清单。
5. 确认并发布申报任务。
6. 查看项目申报进度。
7. 归档过期通知。

---

## P1：建议改，能显著提升工程感

### P1-1. AI 解析人工确认流

| 项目 | 内容 |
|---|---|
| 当前问题 | AI 解析结果直接覆盖 notice 和 material_requirement |
| 改造目标 | AI 解析结果先进入草稿，管理员确认后才写正式材料要求 |
| 涉及模块 | `NoticeService`, `AiService`, `material_requirement`, 新增解析草稿表或字段 |
| 面试价值 | 能讲清楚 AI 工程边界和人机协同 |
| 难度 | 中高 |

推荐新增表：`notice_parse_draft`

```sql
notice_parse_draft
- id
- notice_id
- ai_title
- ai_organizer
- ai_deadline
- ai_target_group
- ai_key_points
- ai_materials_json
- raw_ai_response
- status                 -- PENDING / CONFIRMED / REJECTED
- created_by
- confirmed_by
- confirmed_at
- created_at
- updated_at
- is_deleted
```

改造后流程：

```text
上传通知
→ AI 解析
→ 写入 notice_parse_draft
→ notice.parse_status = CONFIRM_PENDING
→ 管理员查看和修改草稿
→ 管理员确认
→ 正式更新 competition_notice
→ 生成 material_requirement
→ notice.status = PUBLISHED
```

注意：

- AI 解析失败不应直接失败整个业务。
- AI 解析结果必须可人工修改。
- 确认前学生不能基于该通知创建项目。
- 如果通知已有关联项目，不允许随意删除材料要求。

---

### P1-2. 状态机设计

| 项目 | 内容 |
|---|---|
| 当前问题 | 状态较零散，状态流转不够清楚 |
| 改造目标 | 通知、项目、材料都有明确状态和合法流转 |
| 涉及模块 | `NoticeStatus`, `ProjectStatus`, `MaterialStatus`, `StatusTransitionService` |
| 面试价值 | 状态机是后端业务系统的重要能力 |
| 难度 | 中 |

#### 通知状态

| 状态 | 含义 |
|---|---|
| DRAFT | 已上传但未解析 |
| PARSING | AI 解析中 |
| CONFIRM_PENDING | AI 解析完成，待管理员确认 |
| PUBLISHED | 已发布，申请人可创建项目 |
| PARSE_FAILED | AI 解析失败，可重试或人工录入 |
| ARCHIVED | 已归档，不再接受申报 |

合法流转：

```text
DRAFT → PARSING → CONFIRM_PENDING → PUBLISHED → ARCHIVED
DRAFT → PARSING → PARSE_FAILED → PARSING
PARSE_FAILED → CONFIRM_PENDING
```

#### 项目状态

| 状态 | 含义 |
|---|---|
| DRAFT | 项目草稿 |
| MATERIAL_INCOMPLETE | 材料未齐 |
| READY_FOR_AI_CHECK | 材料已齐，可 AI 初审 |
| AI_WARNING | AI 初审有风险 |
| AI_PASSED | AI 初审通过 |
| UNDER_REVIEW | 已提交人工审核 |
| REVISION_REQUIRED | 教师退回修改 |
| APPROVED | 审核通过 |

合法流转：

```text
DRAFT → MATERIAL_INCOMPLETE
MATERIAL_INCOMPLETE → READY_FOR_AI_CHECK
READY_FOR_AI_CHECK → AI_WARNING / AI_PASSED
AI_WARNING → REVISION_REQUIRED / UNDER_REVIEW
AI_PASSED → UNDER_REVIEW
UNDER_REVIEW → REVISION_REQUIRED / APPROVED
REVISION_REQUIRED → MATERIAL_INCOMPLETE / READY_FOR_AI_CHECK
```

#### 材料状态

| 状态 | 含义 |
|---|---|
| NOT_SUBMITTED | 未提交 |
| SUBMITTED | 已提交 |
| AI_WARNING | AI 检查有问题 |
| AI_PASSED | AI 检查通过 |
| TEACHER_APPROVED | 教师通过 |
| TEACHER_REVISION_REQUIRED | 教师要求修改 |

核心原则：

1. Controller 不直接改状态。
2. 状态修改集中到 Service。
3. 每次状态变化写入日志或审核记录。
4. 非法流转抛出业务异常。
5. 状态枚举不要使用魔法字符串散落在代码里。

---

### P1-3. 避免 AI 解析删除正式材料要求

| 项目 | 内容 |
|---|---|
| 当前问题 | 通知解析会删除旧 `material_requirement` 并重建 |
| 改造目标 | 已发布或已有项目关联时，不允许直接删除正式要求 |
| 涉及模块 | `NoticeService.parseNotice`, `material_requirement` |
| 面试价值 | 体现数据一致性意识 |
| 难度 | 中 |

改造规则：

1. 未发布通知：允许用确认后的草稿生成正式材料要求。
2. 已发布但没有项目：允许管理员编辑材料要求，但要记录版本。
3. 已有项目关联：不允许删除已有要求，只能追加新要求或停用旧要求。
4. `material_requirement` 建议增加：
   - `status`: ACTIVE / INACTIVE
   - `version_no`
   - `source`: AI / MANUAL
5. 项目创建时只初始化 ACTIVE 的材料要求。

---

### P1-4. 搜索、分页、筛选

| 项目 | 内容 |
|---|---|
| 当前问题 | 列表能力不足，容易像演示项目 |
| 改造目标 | 关键列表支持搜索、分页、状态筛选 |
| 涉及模块 | `NoticeController`, `ProjectController`, `AdminController`, 前端列表页 |
| 面试价值 | 企业后台系统基础能力 |
| 难度 | 低中 |

至少实现：

| 页面 | 能力 |
|---|---|
| 通知列表 | 标题、主办方、状态、截止日期范围 |
| 项目列表 | 项目名、负责人、状态、通知 ID |
| 用户列表 | 用户名、真实姓名、角色 |
| 材料列表 | 提交状态、审核状态 |
| 消息列表 | 已读 / 未读、时间倒序 |

统一分页参数：

```text
pageNum
pageSize
keyword
status
startDate
endDate
```

---

### P1-5. 统一异常处理和返回结构

| 项目 | 内容 |
|---|---|
| 当前问题 | 不同 Controller 处理方式可能不一致 |
| 改造目标 | 所有接口统一响应结构和错误码 |
| 涉及模块 | `GlobalExceptionHandler`, `Result<T>`, `BusinessException` |
| 面试价值 | 基础工程规范 |
| 难度 | 低中 |

建议响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

错误码示例：

| code | 含义 |
|---:|---|
| 400 | 参数错误 |
| 401 | 未登录 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 409 | 状态冲突 / 重复提交 |
| 500 | 系统异常 |
| 600 | AI 服务降级 |

---

### P1-6. README 和演示链路重写

| 项目 | 内容 |
|---|---|
| 当前问题 | 项目定位需要升级，能力边界需要讲清楚 |
| 改造目标 | README 适合面试官快速理解 |
| 涉及模块 | README、截图、演示账号 |
| 面试价值 | 项目展示能力 |
| 难度 | 低 |

README 必须包括：

1. 项目简介。
2. 项目背景。
3. 核心业务流程图。
4. 系统角色。
5. 核心功能。
6. AI 能力与边界。
7. 技术栈。
8. 数据库设计。
9. 权限设计。
10. 状态流转。
11. 异常与降级。
12. 本地启动。
13. 演示账号。
14. 当前限制。
15. 后续优化。

---

### P1-7. Redis 缓存与防重复提交

| 项目 | 内容 |
|---|---|
| 当前问题 | 没有任何缓存，Dashboard 每次查 5-6 张表；材料上传无幂等保护 |
| 改造目标 | Dashboard 聚合缓存、防重复提交、AI 任务并发控制 |
| 涉及模块 | 新增 `RedisConfig`、`RedisService`，`DashboardController`、`MaterialService`、`AgentService` |
| 面试价值 | 缓存策略、幂等性、分布式锁、原子操作 —— 这是面试官必问的中间件能力 |
| 难度 | 中 |

具体改造场景：

| 场景 | Redis 用法 | Key 设计 | 过期策略 |
|------|-----------|---------|---------|
| Dashboard bootstrap 聚合数据 | 缓存整个响应 Map（JSON 序列化） | `dashboard:bootstrap:{userId}` | 5 分钟过期 |
| 已发布通知列表 | 缓存热门查询结果 | `notice:published:list:{page}:{size}` | 10 分钟过期 |
| 防重复提交材料 | `SETNX` 加锁，提交完释放 | `material:upload:{projectId}:{requirementId}` | 30 秒自动过期 |
| AI 任务并发控制 | `SETNX` 防止同一项目同时跑多个检查 | `ai:check:lock:{projectId}` | 任务完成/超时后删除 |
| 未读消息计数 | 缓存未读数，有新消息或已读时主动失效 | `notify:unread:{userId}` | 有消息变化时主动删除 |

关键设计原则：

1. **缓存不可用时不影响业务**：所有 Redis 操作包裹在 try-catch 中，失败时降级为查数据库。
2. **不使用 Spring Cache 注解**（`@Cacheable`）：用 `RedisTemplate` 手动操作，能在面试中讲清楚序列化方式、原子命令、过期策略。
3. **防止缓存穿透**：查询不到的通知/项目也缓存空值（TTL 1 分钟），避免恶意请求打穿缓存。
4. **防重复提交**：利用 `SETNX` 的原子性，key 过期时间 30 秒兜底（防止程序异常未释放锁）。
5. **主动失效 + TTL 兜底**：数据变更时主动删缓存，TTL 作为最终兜底。

配置要点：

```properties
# Redis 配置（开发环境可选，生产环境推荐）
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
# Redis 不可用时不影响系统启动
spring.cache.type=none
```

```java
// RedisConfig 核心：Jackson 序列化 + 连接失败不抛异常
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    // key 用 String 序列化
    template.setKeySerializer(new StringRedisSerializer());
    // value 用 Jackson JSON 序列化（可读性强，方便调试）
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    return template;
}
```

面试讲法：

> Dashboard 聚合接口一次查 5-6 张表，我加了 Redis 缓存，5 分钟过期。材料上传场景下用 `SETNX` 做了防重复提交，防止用户短时间内重复提交同一材料。缓存不可用时自动降级为查数据库，不影响主流程。面试官如果追问缓存穿透、缓存雪崩、数据一致性问题，这些我都能讲清楚。

---

### P1-8. AI 任务异步化 + 状态追踪

| 项目 | 内容 |
|---|---|
| 当前问题 | AI 解析和 AI 材料检查都是同步调用，前端等 30-120 秒，体验差 |
| 改造目标 | AI 操作异步化，`agent_task_log` 增加中间状态，前端轮询或返回任务 ID |
| 涉及模块 | `AgentService`，`NoticeService.parseNotice()`，`AgentTaskLog` 表，新增 `AsyncConfig` |
| 面试价值 | 异步任务、状态机、轮询/回调、超时重试 —— 这是区分"调了个 API"和"AI 工程化"的关键 |
| 难度 | 中 |

具体改造：

1. **新增 `AsyncConfig`**：
   ```java
   @Configuration
   @EnableAsync
   public class AsyncConfig {
       @Bean("aiTaskExecutor")
       public Executor aiTaskExecutor() {
           ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
           executor.setCorePoolSize(2);
           executor.setMaxPoolSize(5);
           executor.setQueueCapacity(100);
           executor.setThreadNamePrefix("ai-task-");
           executor.setRejectedExecutionHandler(new CallerRunsPolicy());
           return executor;
       }
   }
   ```

2. **`agent_task_log` 增加中间状态**：
   ```sql
   ALTER TABLE agent_task_log
   MODIFY COLUMN execute_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
       COMMENT '执行状态: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT';
   ```

3. **异步改造**：
   - `AgentService.checkMaterial()` → 先创建 `agent_task_log`（status=PENDING），返回 taskId
   - `@Async("aiTaskExecutor")` 执行 AI 调用，更新 status=RUNNING → SUCCESS/FAILED
   - 前端收到 taskId 后，轮询 `GET /agent/task-logs?taskId=X` 或直接用现有列表刷新
   - 同理改造 `NoticeService.parseNotice()` 的 AI 调用部分

4. **超时处理**：`CompletableFuture.orTimeout(120, TimeUnit.SECONDS)`，超时后更新 status=TIMEOUT，写入 fallback 结果

5. **降级保证**：异步执行失败/超时时，系统行为与当前同步降级一致（返回 fallback 结果，不抛异常）

改造后流程：

```text
POST /agent/check-material/{projectId}
→ 创建 agent_task_log (PENDING)
→ 返回 taskId
→ @Async 执行 AI 调用 (RUNNING)
→ LLM 返回 → 更新 review_record + notify_message
→ 更新 agent_task_log (SUCCESS)
———————————————————————
→ 前端轮询 GET /agent/task-logs (每隔 3 秒)
→ 看到 taskId 对应的 status=SUCCESS → 展示结果
```

**注意**：`@Async` 的 AI 任务仍然保留 try-catch + fallback，失败不会中断主流程。将来如果任务量增长，可以平滑切换到消息队列（RocketMQ/RabbitMQ），线程池改为消费者即可，不需要改业务代码。

面试讲法：

> AI 调用（尤其是 PDF OCR）可能耗时几十秒，我把它改成异步的：接口立即返回任务 ID，后台线程池执行，前端轮询状态。任务状态有 PENDING → RUNNING → SUCCESS/FAILED/TIMEOUT 五个状态。失败和超时有降级兜底，不会丢任务。如果将来任务量大了，线程池可以直接换成消息队列消费者，业务逻辑不用改。

---

### P1-9. Swagger / Knife4j 接口文档

| 项目 | 内容 |
|---|---|
| 当前问题 | 没有接口文档，面试官无法直观了解 API 设计 |
| 改造目标 | 加入 Knife4j，所有接口按模块分组，标注权限要求 |
| 涉及模块 | `pom.xml`，新增 `Knife4jConfig`，各 Controller 加 `@Tag` / `@Operation` |
| 面试价值 | 专业项目标配，面试官打开 `http://localhost:8080/doc.html` 就能看到接口结构和权限 |
| 难度 | 低 |

具体改造：

1. `pom.xml` 加 Knife4j 依赖（基于 SpringDoc OpenAPI）。
2. 新增 `Knife4jConfig` 配置分组（认证、通知、项目、材料、AI、消息）。
3. 每个 Controller 加 `@Tag(name = "xxx")`，每个方法加 `@Operation(summary = "xxx")`。
4. DTO 字段加 `@Schema(description = "xxx")` 注解。
5. `SecurityConfig` 放行 `/doc.html`、`/v3/api-docs/**`、`/webjars/**`。

面试讲法：

> 我集成了 Knife4j，所有接口都有在线文档和在线调试功能，按模块分组，标注了权限要求。面试官访问 `/doc.html` 就能看到。

---

### P1-10. 定时任务 - 截止日期提醒与超时任务兜底

| 项目 | 内容 |
|---|---|
| 当前问题 | 没有主动提醒机制，项目过期了用户可能不知道 |
| 改造目标 | 定时检查即将到期的项目，自动发通知；兜底处理超时的异步 AI 任务 |
| 涉及模块 | 新增 `ScheduledTaskConfig`、`ScheduleService`，`NotifyMessageMapper` |
| 面试价值 | 能讲定时任务、分布式调度、幂等性、Spring Scheduled vs XXL-JOB |
| 难度 | 低中 |

具体改造：

1. **截止日期提醒**（每天 9:00 执行）：
   ```java
   @Scheduled(cron = "0 0 9 * * ?")
   public void checkDeadlineReminders() {
       // 查询 deadline 在 3 天内的项目
       // 对每个项目负责人发 notify_message (msg_type=deadline)
       // 每个项目每天只发一次（检查是否已发过）
   }
   ```

2. **AI 超时任务兜底**（每 5 分钟执行）：
   ```java
   @Scheduled(fixedRate = 300000)
   public void handleTimeoutAiTasks() {
       // 查询 status=RUNNING 且 created_at 超过 2 分钟的 agent_task_log
       // 更新 status=TIMEOUT，写入 fallback 结果
   }
   ```

3. 启用定时任务：`@EnableScheduling` 在 `AsyncConfig` 或单独的配置类中。

面试讲法：

> 我用 `@Scheduled` 做了截止日期提醒和超时任务兜底两个定时任务。面试官如果问多实例部署时重复执行怎么办，我的回答是：目前单实例部署，如果将来扩展，可以用 Redis 分布式锁或接入 XXL-JOB 做分布式调度，代码已经按 service 方法拆分好了，切换成本很低。

---

### P1-11. 单元测试与权限测试

| 项目 | 内容 |
|---|---|
| 当前问题 | 只有一个默认的上下文加载测试 |
| 改造目标 | 覆盖核心 Service 方法 + 权限拦截 + 状态机 |
| 涉及模块 | `src/test/java/`，引入 `spring-boot-starter-test` + `h2`（测试用内存数据库） |
| 面试价值 | 体现质量意识和专业习惯 |
| 难度 | 中 |

最小测试集（至少 8 个测试类）：

| 测试类 | 覆盖内容 |
|--------|---------|
| `AuthServiceTest` | 登录成功、密码错误、用户不存在 |
| `JwtAuthTest` | 未登录访问业务接口 → 401，学生访问管理接口 → 403 |
| `ProjectServiceTest` | 创建项目、非成员不能查看项目详情 |
| `MaterialServiceTest` | 上传材料 → 版本号递增、非项目成员上传被拒绝 |
| `MaterialReviewTest` | 学生调用审核接口 → 403，教师审核 → 成功 |
| `NoticeServiceTest` | AI 解析失败 → 降级不抛异常、已关联项目时不能删除材料要求 |
| `StatusTransitionTest` | draft → incomplete → ready → approved 合法流转，非法流转抛异常 |
| `AiServiceTest` | cleanJson 正确提取 JSON、fallback 返回降级结果 |

面试讲法：

> 我写了 8 个测试类覆盖核心业务、权限和状态流转。权限测试验证了未登录返回 401、越权访问返回 403。状态流转测试验证了合法流转可以通过、非法流转被拦截。AI 测试验证了降级策略。

---

### P1-12. Docker Compose 一键启动

| 项目 | 内容 |
|---|---|
| 当前问题 | 面试官 clone 后需要分别启动 MySQL、后端、前端，门槛高 |
| 改造目标 | 一个 `docker-compose up` 启动全部服务（MySQL + Redis + 后端 + 前端） |
| 涉及模块 | 新增 `docker-compose.yml`、`.env.example` |
| 面试价值 | 部署能力是后端工程师的基本要求 |
| 难度 | 低中 |

提供文件：

```
docker-compose.yml          # MySQL 8.0 + Redis 7 + 后端 + 前端
.env.example                # 环境变量模板（无真实密钥）
Dockerfile.backend          # 后端多阶段构建
Dockerfile.frontend         # 前端 Nginx 部署
init.sql                    # 初始化数据库 + 演示账号
```

面试讲法：

> 项目用 Docker Compose 管理依赖，MySQL、Redis、后端、前端一键启动，`.env.example` 提供配置模板，面试官 clone 后改一下环境变量就能跑起来。

---

## P2：有时间再做，加分但不影响主线

### P2-1. 文件存储从数据库扩展到本地/MinIO

| 项目 | 内容 |
|---|---|
| 当前问题 | 文件存 LONGBLOB，不适合大规模使用 |
| 改造目标 | 抽象 FileStorageService，支持数据库和本地/MinIO 两种实现 |
| 面试价值 | 能讲清楚对象存储和数据库存文件的取舍 |
| 难度 | 中高 |

建议：

```text
FileStorageService
- save(MultipartFile file): FileAsset
- load(fileId): InputStream
- delete(fileId)
```

短期可以不接 MinIO，只抽象接口并支持本地目录存储。

---

### P2-2. 操作日志与审计

| 项目 | 内容 |
|---|---|
| 当前问题 | 有 AI 任务日志，但普通业务操作日志不足 |
| 改造目标 | 记录关键业务操作 |
| 面试价值 | 企业系统审计意识 |
| 难度 | 中 |

记录：

- 上传通知
- 确认 AI 解析
- 发布通知
- 创建项目
- 上传材料
- 运行 AI 初审
- 教师审核
- 管理员修改用户角色

---

## 6. 建议数据库变更

### 6.1 不建议大规模改表名

短期不建议把 `competition_notice`、`competition_project` 全部改名为 `application_notice`、`application_project`。

原因：

1. 改动大。
2. 容易引入 bug。
3. 对面试价值提升不明显。
4. README 和前端文案可以先完成定位升级。

建议：

- 数据库表名暂时保留。
- 实体和接口逐步增加更通用的字段。
- README 中说明最初从竞赛场景抽象而来。

### 6.2 建议新增 / 修改字段

#### competition_notice

```sql
ALTER TABLE competition_notice
ADD COLUMN notice_type VARCHAR(32) DEFAULT 'COMPETITION' COMMENT '申报类型：COMPETITION/RESEARCH/SCHOLARSHIP/OTHER',
ADD COLUMN parse_status VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'AI解析状态',
ADD COLUMN publish_status VARCHAR(32) DEFAULT 'DRAFT' COMMENT '发布状态',
ADD COLUMN confirmed_by BIGINT NULL COMMENT '确认人',
ADD COLUMN confirmed_at DATETIME NULL COMMENT '确认时间',
ADD COLUMN published_at DATETIME NULL COMMENT '发布时间';
```

#### notice_parse_draft

```sql
CREATE TABLE notice_parse_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notice_id BIGINT NOT NULL,
  ai_title VARCHAR(255),
  ai_organizer VARCHAR(255),
  ai_deadline DATETIME,
  ai_target_group VARCHAR(255),
  ai_key_points TEXT,
  ai_materials_json LONGTEXT,
  raw_ai_response LONGTEXT,
  status VARCHAR(32) DEFAULT 'PENDING',
  created_by BIGINT,
  confirmed_by BIGINT,
  confirmed_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT DEFAULT 0
);
```

#### material_requirement

```sql
ALTER TABLE material_requirement
ADD COLUMN status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
ADD COLUMN source VARCHAR(32) DEFAULT 'AI' COMMENT 'AI/MANUAL',
ADD COLUMN version_no INT DEFAULT 1 COMMENT '材料要求版本';
```

#### project_material

```sql
ALTER TABLE project_material
ADD COLUMN ai_review_result VARCHAR(32) NULL COMMENT 'AI检查结果',
ADD COLUMN ai_review_comment TEXT NULL COMMENT 'AI检查意见',
ADD COLUMN ai_checked_at DATETIME NULL COMMENT 'AI检查时间';
```

---

## 7. 建议接口设计

### 7.1 认证与用户

| 方法 | 接口 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/auth/register` | 注册 | 放行 |
| POST | `/api/auth/login` | 登录 | 放行 |
| GET | `/api/auth/me` | 当前用户 | 登录 |
| GET | `/api/admin/users` | 用户列表 | ADMIN |
| POST | `/api/admin/users` | 创建用户 | ADMIN |
| PUT | `/api/admin/users/{id}/role` | 修改角色 | ADMIN |
| DELETE | `/api/admin/users/{id}` | 删除用户 | ADMIN |

### 7.2 申报通知

| 方法 | 接口 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/notices` | 上传通知 | ADMIN |
| POST | `/api/notices/{id}/parse` | 触发 AI 解析 | ADMIN |
| GET | `/api/notices/{id}/parse-draft` | 查看解析草稿 | ADMIN |
| PUT | `/api/notices/{id}/parse-draft` | 修改解析草稿 | ADMIN |
| POST | `/api/notices/{id}/confirm` | 确认解析结果 | ADMIN |
| POST | `/api/notices/{id}/publish` | 发布通知 | ADMIN |
| GET | `/api/notices` | 通知列表 | 登录 |
| GET | `/api/notices/{id}` | 通知详情 | 登录 |
| POST | `/api/notices/{id}/archive` | 归档通知 | ADMIN |

### 7.3 申报项目

| 方法 | 接口 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/projects` | 创建项目 | STUDENT / ADMIN |
| GET | `/api/projects/my` | 我的项目 | 登录 |
| GET | `/api/projects/{id}` | 项目详情 | 项目成员 / 教师 / ADMIN |
| POST | `/api/projects/{id}/members` | 添加成员 | 项目负责人 / ADMIN |
| DELETE | `/api/projects/{id}/members/{userId}` | 移除成员 | 项目负责人 / ADMIN |
| POST | `/api/projects/{id}/submit` | 提交人工审核 | 项目负责人 |

### 7.4 材料与审核

| 方法 | 接口 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/projects/{projectId}/materials/{requirementId}/upload` | 上传材料 | 项目成员 |
| POST | `/api/projects/{projectId}/ai-check` | AI 初审 | 项目成员 / ADMIN |
| POST | `/api/projects/{projectId}/materials/{materialId}/review` | 教师审核 | TEACHER / ADMIN |
| GET | `/api/projects/{projectId}/materials` | 材料列表 | 项目相关人员 |
| GET | `/api/files/{fileId}` | 文件下载/预览 | 有资源权限 |

### 7.5 消息通知

| 方法 | 接口 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/notifications/my` | 我的消息 | 登录 |
| POST | `/api/notifications/{id}/read` | 标记已读 | 消息接收人 |
| POST | `/api/notifications/read-all` | 全部已读 | 登录 |

---

## 8. 前端改造方案

### 8.1 管理员端

新增或调整页面：

| 页面 | 功能 |
|---|---|
| 通知管理 | 上传通知、查看解析状态、发布/归档 |
| AI 解析确认 | 查看 AI 提取字段，编辑材料清单，确认发布 |
| 项目总览 | 查看所有申报项目和状态 |
| 用户管理 | 保留现有管理员用户 CRUD |

### 8.2 学生端

调整页面：

| 页面 | 功能 |
|---|---|
| 已发布通知列表 | 搜索、筛选、查看详情 |
| 创建申报项目 | 只能基于 PUBLISHED 通知创建 |
| 项目详情 | 成员、材料清单、完成率、AI 初审、人工审核意见 |
| 消息中心 | AI 警告、教师退回、审核通过 |

### 8.3 教师端

调整页面：

| 页面 | 功能 |
|---|---|
| 待审核项目列表 | 按状态筛选 |
| 材料审核面板 | 文件预览、审核意见、通过/退回 |
| 审核记录 | 查看历史审核意见 |

---

## 9. 两周最小可执行版本

### 第 1 周：修安全和身份

| 天数 | 任务 | 验收 |
|---|---|---|
| Day 1 | 新增 JWT Filter，接入 Spring Security | 未登录业务接口返回 401 |
| Day 2 | 实现 CurrentUser 获取工具 | Controller 不再手动解析 token |
| Day 3 | 去掉默认 `createdBy` / `uploadedBy` / `reviewerId` | 操作人全部来自登录态 |
| Day 4 | 管理员接口加 ADMIN 权限 | 学生访问 `/admin/**` 返回 403 |
| Day 5 | 项目详情、材料上传、材料审核加资源权限 | 非项目成员无法操作 |
| Day 6 | 清理密钥、补 `.env.example` | 仓库无真实密码 |
| Day 7 | 写一轮权限测试说明和 README 更新 | 可演示 401/403/越权拦截 |

### 第 2 周：AI 确认流和状态机

| 天数 | 任务 | 验收 |
|---|---|---|
| Day 8 | 新增 notice_parse_draft 表和实体 | AI 解析结果先写草稿 |
| Day 9 | 管理员查看/修改解析草稿接口 | 草稿可编辑 |
| Day 10 | 确认解析结果，生成正式材料要求 | 确认后才写 `material_requirement` |
| Day 11 | 通知状态流转 | DRAFT/PARSING/CONFIRM_PENDING/PUBLISHED 可跑通 |
| Day 12 | 项目和材料状态流转整理 | 非法状态流转被拦截 |
| Day 13 | 通知搜索、项目筛选、成员搜索 | 基础列表可用 |
| Day 14 | 完成 README、演示数据、演示脚本 | 项目可以作为主项目展示 |

---

## 10. 四周完整改造版本

| 周次 | 目标 | 关键产出 |
|---|---|---|
| 第 1 周 | 安全和权限 | JWT Filter、CurrentUser、角色权限、资源权限、密钥清理 |
| 第 2 周 | AI 确认流和状态机 | 解析草稿、确认发布、状态流转、材料要求保护 |
| 第 3 周 | Redis + 异步 AI + 搜索分页 + 接口文档 | Redis 缓存/防重复、@Async AI 任务、Knife4j、列表搜索筛选 |
| 第 4 周 | 定时任务 + 测试 + Docker + 面试材料 | 截止提醒/超时兜底、8 个测试类、Docker Compose、演示脚本、面试追问表 |

---

## 11. 面试讲法

### 11.1 项目背景

可以这样讲：

> 这个项目最初从高校竞赛材料管理场景出发，后来我发现它的本质是材料申报和审核流程：管理员发布申报要求，申请人按清单提交材料，系统做完整性检查，审核人复核并给出意见。因此我把它抽象为 AI 材料申报与审核平台，用 AI 解决通知要求分散、材料漏交、人工初审重复的问题。

### 11.2 核心亮点

推荐讲 7 个（按面试惊艳度排序）：

1. **AI 解析 + 人工确认流**：AI 生成草稿，管理员确认后才发布 —— 人机协同，不为 AI 而 AI。
2. **AI 异步任务 + 状态追踪**：`@Async` 线程池执行，PENDING→RUNNING→SUCCESS/FAILED/TIMEOUT 五状态流转，前端轮询，超时降级。
3. **权限与资源隔离**：JWT Filter 接入 Spring Security，角色权限 + 项目归属校验，401/403 完整覆盖。
4. **Redis 缓存 + 防重复提交**：Dashboard 聚合缓存 5min，`SETNX` 防重复提交材料，缓存穿透保护，降级兜底。
5. **材料版本管理**：`(project_id, requirement_id, version_no)` 唯一约束，每次上传自动判断覆盖还是新建版本。
6. **AI 两阶段降级策略**：LLM 不可用 → fallback 默认结果；Tika 提取失败 → PDFBox 渲染 + qwen-vl-plus 视觉 OCR —— 两级兜底，AI 故障不阻塞业务流程。
7. **状态机设计**：通知 6 状态、项目 8 状态、材料 6 状态，非法流转抛异常，状态变化写审计日志。

### 11.3 可主动承认的边界

建议主动说：

> 这个系统不是通用 OA 流程引擎，目前审批流程是固定的“申请人提交 → AI 初审 → 教师/管理员复核”。我没有引入复杂流程引擎，是因为项目目标是展示材料申报审核场景下的后端工程设计和 AI 辅助能力，而不是做低代码 OA 平台。

这样更稳。

---

## 12. 面试追问题准备

| 问题 | 应答方向 |
|---|---|
| 为什么不用”竞赛系统”这个定位？ | 竞赛只是材料申报场景的一种，抽象后更通用 |
| AI 解析错了怎么办？ | 解析结果先进入草稿，管理员确认后才发布 |
| 怎么防止学生审核自己的材料？ | 审核接口从登录态取用户，并校验 TEACHER/ADMIN 和项目关系 |
| 怎么防止越权查看项目？ | 项目详情和材料接口校验当前用户是否为成员/教师/管理员 |
| 为什么不让 AI 直接决定通过？ | AI 只做初审和风险提示，最终由人工复核 |
| 文件为什么一开始存数据库？ | 项目早期简化部署；后续通过 FileStorageService 抽象切到对象存储 |
| 通知重新解析会不会破坏已有项目？ | 已有关联项目后不能删除正式材料要求，只能追加或停用 |
| 为什么不做完整 OA？ | 目标聚焦材料申报审核，不做通用流程引擎 |
| 项目工程化体现在哪里？ | 权限、状态机、版本、审计、异常降级、测试和部署 |
| **Redis 在项目中怎么用的？** | Dashboard 聚合数据 5min 缓存、材料上传 `SETNX` 防重复提交、AI 任务并发锁、未读计数缓存 —— 全部降级兜底，Redis 挂了不影响业务 |
| **AI 调用同步还是异步？** | 异步。`@Async` 线程池执行，立即返回 taskId，前端轮询。任务状态 PENDING→RUNNING→SUCCESS/FAILED/TIMEOUT。超时 2 分钟自动标记，`@Scheduled` 兜底 |
| **怎么防止重复提交材料？** | Redis `SETNX` 对 `projectId:requirementId` 加 30 秒分布式锁，配合数据库 `(project_id, requirement_id, version_no)` 唯一约束做双重保障 |
| **缓存穿透怎么处理？** | 查不到的数据也缓存空值（TTL 1 分钟），`ConcurrentHashMap` 做本地缓存标记，防止恶意请求打穿 Redis |
| **定时任务多实例怎么办？** | 目前单实例部署；代码已拆分为独立 service 方法，若多实例部署可用 Redis `SETNX` 分布式锁或用 XXL-JOB 等分布式调度框架 |
| **怎么测试权限？** | 集成测试：MockMvc + `@WithMockUser` 模拟不同角色，验证 401/403/200 |
| **Swagger 给面试官看什么？** | `http://localhost:8080/doc.html` — 接口按认证/通知/项目/材料/AI/消息分组，标注了权限要求，支持在线调试 |

---

## 13. README 改写建议

README 第一屏建议写：

```markdown
# AI 材料申报与审核平台

本项目面向高校竞赛、科研项目、奖学金等材料申报场景，提供申报通知上传、AI 结构化解析、材料清单生成、申请人材料提交、AI 完整性初审、教师/管理员复核、状态追踪与消息通知能力。

系统不会让 AI 直接替代人工审核，而是采用“AI 生成草稿 / 初审意见 + 人工确认 / 复核”的人机协同模式，兼顾效率和可靠性。
```

README 核心目录：

```markdown
## 项目背景
## 核心流程
## 系统角色
## 核心功能
## AI 能力与边界
## 技术栈
## 系统架构
## 数据库设计
## 权限设计
## 状态流转
## 异常与降级处理
## 本地启动
## 演示账号
## 当前限制
## 后续优化
```

---

## 14. 给 Claude / Codex 的开发约束

后续应写入 `CLAUDE.md` 或 `AGENTS.md`：

```markdown
# 开发约束

1. 修改前先阅读相关 Controller、Service、Mapper、Entity 和前端调用。
2. 修改前先输出计划，不直接大规模改代码。
3. 不允许绕过后端权限校验。
4. 不允许用默认 userId 代替当前登录用户。
5. 不允许只靠前端隐藏按钮实现权限。
6. AI 解析结果不能直接替代人工最终确认。
7. 状态修改必须经过统一 Service 校验。
8. 新增接口必须有参数校验、异常处理和统一返回。
9. 新增字段必须说明用途和兼容旧数据。
10. 不引入微服务、流程引擎、复杂中间件。
11. 不删除已有可用功能。
12. 每次改动后必须给出测试方式。
```

---

## 15. 最终验收标准

### 15.1 功能验收

| 验收项 | 标准 |
|---|---|
| 登录鉴权 | 未登录不能访问业务接口 |
| 角色权限 | 学生不能访问管理员接口，不能审核材料 |
| 当前用户 | 所有操作人来自 JWT |
| 通知解析 | AI 结果先生成草稿 |
| 人工确认 | 管理员确认后才生成材料要求 |
| 通知发布 | 未发布通知不能创建项目 |
| 材料上传 | 只能项目成员上传 |
| 材料审核 | 只有教师/管理员可审核 |
| 消息查看 | 用户只能查看自己的消息 |
| 状态流转 | 非法状态变化被拦截 |
| 搜索分页 | 通知、项目、用户支持基础筛选 |
| **Redis 缓存** | Dashboard 数据 5min 缓存，缓存失效后自动查库 |
| **防重复提交** | 同一材料 30 秒内不能重复上传 |
| **AI 异步化** | AI 调用返回 taskId，前端轮询状态，超时自动降级 |
| **接口文档** | `http://localhost:8080/doc.html` 所有接口可在线查看和调试 |
| **定时任务** | 截止日期 3 天前自动发通知提醒 |
| **Docker Compose** | `docker-compose up` 一键启动全部服务 |
| **测试** | 8 个测试类覆盖权限、业务、状态机、AI 降级 |
| README | 项目定位、流程、边界清楚 |

### 15.2 面试验收

你需要能在 3 分钟内讲清：

1. 项目为什么从竞赛系统升级为材料申报审核平台。
2. 系统核心业务链路。
3. AI 在系统中做什么，不做什么。
4. 权限系统怎么实现。
5. 状态机怎么设计。
6. 遇到过哪些问题，如何改造。
7. 当前边界和后续扩展。

---

## 16. 最终结论

这个项目不需要推倒重做，也不适合包装成完整 OA 或企业级流程平台。

最合理的升级路线是：

> 从”AI 竞赛材料管理系统”升级为”AI 材料申报与审核平台”。

核心不是增加更多页面，而是补齐 **12 项工程化能力**：

| 序号 | 能力 | 位置 | 面试价值 |
|------|------|------|---------|
| 1 | JWT 鉴权 + Spring Security 整合 | P0-1 | 认证 vs 鉴权、SecurityContext |
| 2 | 角色权限 + 资源归属校验 | P0-2/3 | 越权防护、401/403 |
| 3 | AI 解析人工确认流 | P1-1 | AI 工程边界、人机协同 |
| 4 | 状态机设计 | P1-2 | 后端业务建模能力 |
| 5 | 搜索分页筛选 | P1-4 | 企业后台基础能力 |
| 6 | **Redis 缓存 + 防重复提交** | **P1-7（新增）** | 缓存策略、幂等性、分布式锁 |
| 7 | **AI 异步任务 + 状态追踪** | **P1-8（从 P2 提升）** | 异步化、线程池、超时重试 |
| 8 | **Swagger/Knife4j 接口文档** | **P1-9（新增）** | 专业项目标配 |
| 9 | **定时任务（截止提醒 + 超时兜底）** | **P1-10（新增）** | 定时调度、分布式思考 |
| 10 | **单元测试与权限测试** | **P1-11（从 P2 提升）** | 质量意识 |
| 11 | **Docker Compose 一键启动** | **P1-12（从 P2 提升）** | 部署能力 |
| 12 | 数据一致性（材料要求保护） | P1-3 | 边界条件思考 |

完成这些后，面试时你能讲出一条完整的技术故事：

> “我从一个竞赛材料管理系统出发，把它升级为通用的材料申报与审核平台。在这个过程中，我补齐了 JWT 鉴权 + 角色/资源权限校验，设计了通知/项目/材料三级状态机，AI 解析加入人工确认流防止错误落库，用 Redis 做了 Dashboard 缓存和防重复提交，AI 调用异步化并追踪任务状态，加了截止日期提醒定时任务，写了 8 个测试类覆盖权限和业务，最后用 Docker Compose 一键部署。这个项目展示了我在权限设计、状态流转、中间件使用、异步任务、测试和部署方面的能力。”

这份方案改造完成后，项目会从一个”有 AI 功能的课程项目”变成一个在 Java 后端实习面试中能**全面展示工程化能力**的项目。
