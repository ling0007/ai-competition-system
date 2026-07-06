# AI 竞赛材料管理系统 — 产品文档

> **版本**: 1.1 | **日期**: 2026-06-19 | **作者**: 产品技术团队

---

## 目录

1. [产品概述](#一产品概述)
2. [功能全景](#二功能全景)
3. [功能模块详解](#三功能模块详解)
4. [技术架构](#四技术架构)
5. [API 参考](#五api-参考)
6. [部署运维](#六部署运维)
7. [开发指南](#七开发指南)

---

## 一、产品概述

### 1.1 产品定位

**AI 竞赛材料管理系统** 是一款面向高校师生团队的竞赛申报全流程管理平台。系统将竞赛通知发布、项目申报组建、材料提交审核、AI 智能核验整合为一体化工作流，解决传统竞赛管理中"信息传递滞后、材料格式混乱、人工审核效率低"三大痛点。

### 1.2 目标用户

| 用户角色 | 典型场景 | 核心诉求 |
|----------|----------|----------|
| **学生**（参赛团队队长/队员） | 查看竞赛通知、组建参赛团队、提交申报材料 | 快速了解材料要求、避免遗漏、及时获取审核反馈 |
| **教师**（指导老师/评委） | 指导多个参赛团队、审核学生提交的材料 | 批量查看项目进度、高效完成材料审核、统一评审标准 |
| **管理员**（系统管理员/教务处） | 发布竞赛通知、管理用户账号、系统运维 | 全局掌控竞赛进展、灵活分配角色权限 |

### 1.3 核心价值

- **AI 提效**：通知自动解析 + 材料智能检查，减少 80% 人工核对工作
- **全流程数字化**：从通知发布到材料归档，一个平台闭环管理
- **实时进度可视**：材料完成率自动计算，项目状态一目了然
- **零成本部署**：支持 Docker 一键部署 + 免费云端方案（Render + Aiven MySQL）

---

## 二、功能全景

### 2.1 角色功能矩阵

| 功能模块 | 学生 | 教师 | 管理员 |
|----------|:----:|:----:|:------:|
| 用户注册/登录 | ✓ | ✓ | ✓ |
| 个人资料编辑 | ✓ | ✓ | ✓ |
| 修改密码 | ✓ | ✓ | ✓ |
| 查看竞赛通知 | ✓ | ✓ | ✓ |
| **上传/发布通知** | — | ✓ | ✓ |
| **AI 解析通知** | — | ✓ | ✓ |
| **创建参赛项目** | ✓ | — | — |
| **管理项目成员** | ✓（队长） | — | — |
| **上传申报材料** | ✓ | — | — |
| **运行 AI 材料检查** | ✓ | — | — |
| **查看项目进度** | ✓ | ✓ | ✓ |
| **审核材料（教师）** | — | ✓ | — |
| **查看审核反馈** | ✓ | ✓ | ✓ |
| **消息中心（查看通知）** | ✓ | ✓ | — |
| **审计日志（查看 AI 调用记录）** | ✓ | ✓ | ✓ |
| **用户管理（CRUD）** | — | — | ✓ |
| **角色分配** | — | — | ✓ |
| **文件下载/预览** | ✓ | ✓ | ✓ |

### 2.2 核心业务流程

```
教师/管理员上传竞赛通知
        │
        ▼
AI 自动解析通知内容（主办方、截止日期、材料要求）
        │
        ▼
    结构化展示（通知列表 + 材料要求清单）
        │
        ▼
学生选择通知 → 创建参赛项目
        │
        ├── 添加团队成员（队长/队员/指导老师）
        │
        ▼
逐项上传申报材料（支持多版本重新提交）
        │
        ▼
运行 AI 材料完整性检查
        │
        ├── 通过 → 等待教师最终审核
        ├── 警告 → 查看 AI 建议 → 修改材料后重新上传
        └── 驳回 → 修改材料后重新上传
        │
        ▼
教师审核材料
        │
        ├── 通过 → 材料审核完成
        └── 需修改 → 填写修改建议 → 通知学生
	        │
	        ▼
	学生通过消息中心接收审核反馈
	        │
	        ▼
	全部 AI 调用自动记录至审计日志
```

### 2.3 项目状态流转

```
draft（草稿） → incomplete（材料不全） → ready（材料就绪） → reviewed（已审核）
     │                    │                      │
     └── 0% 完成率 ──────┴── 1%-99% 完成率 ──────┴── 100% 完成率
```

---

## 三、功能模块详解

### 3.1 用户认证与权限管理

#### 注册与登录

- **注册**：填写用户名、真实姓名、密码（BCrypt 加密存储），自动分配学生角色
- **登录**：用户名 + 密码 → 返回 JWT Token（24 小时有效期），含 userId、username、role
- **Token 使用**：前端 Axios 拦截器自动附加 `Authorization: Bearer <token>` 头

#### 角色体系

系统内置三级角色，按角色区隔功能：

| 角色 | 数据库值 | 默认权限范围 |
|------|----------|-------------|
| 学生 | `student` | 创建项目、上传材料、AI 检查、查看进度 |
| 教师 | `teacher` | 查看所有项目、审核材料、管理成员 |
| 管理员 | `admin` | 用户管理（CRUD）、角色分配、全局操作 |

#### 个人账号管理

- **个人资料编辑**：修改用户名、真实姓名、手机号
- **修改密码**：需验证旧密码后更新
- **安全说明**：Spring Security 配置为开放所有端点（`permitAll()`），认证通过控制器层手动解析 JWT 实现，CORS 允许所有来源

---

### 3.2 竞赛通知管理

#### 通知发布

教师或管理员通过通知面板发布竞赛通知，支持两种信息输入方式：

| 输入方式 | 说明 |
|----------|------|
| **表单字段** | 手动填写标题、主办方、截止日期、面向对象 |
| **文本内容** | 粘贴通知原始文本（兼容直接输入或 AI 解析后填充） |
| **文件上传** | 上传 PDF/DOCX/TXT 格式的通知文件，系统自动提取文本 |

#### AI 智能解析

触发 AI 解析后，系统将通知文本（最长 4000 字符）发送至 **DashScope qwen-turbo** 模型，自动提取：

| 解析字段 | 说明 |
|----------|------|
| 主办方（organizer） | 竞赛主办单位或组织 |
| 截止日期（deadline） | 材料提交截止时间 |
| 面向对象（targetGroup） | 参赛资格要求 |
| 关键要点（keyPoints） | 通知中的重要说明 |
| 材料要求清单（materials） | 需要提交的材料项（名称、是否必交、说明） |

解析结果自动回填到通知实体，同时生成 `material_requirement` 记录，指导后续材料提交。

#### 降级保障

当 DashScope API 不可达或 API Key 未配置时，系统返回中文降级提示（"AI 解析暂时不可用"），不影响系统正常运行。通知可由教师手动填写完整。

---

### 3.3 项目申报与成员管理

#### 创建项目

学生选择一条通知后创建参赛项目：

- **项目名称**：参赛项目的正式名称
- **团队名称**：可选，团队标识
- **队长**：创建者自动担任
- **指导老师**：从用户列表中选择（角色为 teacher 的用户）
- **队员**：可添加多个队员
- **截止日期**：自动同步通知中的截止时间

创建成功后，系统自动为通知中所有必交材料生成 `project_material` 记录（初始状态 `pending`）。

#### 成员管理

| 操作 | 权限 | 说明 |
|------|------|------|
| 添加成员 | 队长 | 选择用户并指定角色（leader/member/advisor） |
| 移除成员 | 队长 | 队长不可被移除；唯一约束防止重复添加 |
| 查看成员 | 所有人 | 项目详情中展示成员列表及角色 |

---

### 3.4 材料提交与版本管理

#### 材料要求

每个竞赛通知包含一组材料要求（`material_requirement`），由 AI 解析或教师手动定义：

- **名称**（requirementName）：如 "项目申报书"、"团队成员表"
- **是否必交**（isRequired）：必交材料计入完成率计算
- **说明**（description）：对该材料的详细要求

#### 上传材料

学生逐项上传材料文件：

- **支持格式**：PDF、DOCX、XLSX、TXT 等常见文档格式
- **大小限制**：单文件最大 50MB
- **版本管理**：每次上传自动递增版本号（`version_no`），保留所有历史版本
- **备注**：可添加补充说明文本

#### 自动进度计算

系统通过数据库触发器实现实时进度更新：

```
project_material INSERT/UPDATE 触发
        │
        ▼
sp_refresh_project_progress(project_id)
        │
        ├── 统计必交材料总数
        ├── 统计已提交（submit_status='submitted'）数量
        ├── 计算 completion_rate = 已提交数 / 总数 × 100%
        └── 更新项目状态：
              ├── 0%  → draft（草稿）
              ├── 1-99% → incomplete（材料不全）
              └── 100% → ready（材料就绪）
```

#### 提交状态说明

| 状态值 | 含义 | 显示 |
|--------|------|------|
| `pending` | 尚未提交 | 待提交 |
| `submitted` | 已提交，待审核 | 已提交 |
| `rejected` | 已被驳回 | 已驳回 |

---

### 3.5 AI 材料检查

#### 文件文本提取

AI 检查的第一步是提取上传文件中的文本内容，系统采用两阶段提取管线：

**阶段一：Apache Tika 文本提取**

使用 Apache Tika 3.1.0 的 `AutoDetectParser` 自动识别文件格式并提取文本，支持 DOCX、PDF、XLSX、TXT 等格式。提取结果限制在 100,000 字符以内。

**阶段二：PDF OCR 视觉提取（降级方案）**

当 Tika 无法从 PDF 中提取有效文本时（如扫描件、图片型 PDF），自动触发 OCR 管线：

1. **PDFBox 渲染**：将 PDF 页面渲染为 JPEG 图片（100 DPI，最大 3 页，最大宽度 1200px）
2. **并行 OCR**：使用 `CompletableFuture` 将多页图片并发发送至 `qwen-vl-plus` 视觉模型，每页 120 秒超时
3. **结果合并**：汇总所有页面的识别文本

整个管线保证不抛出异常，确保 AI 检查流程不会因文件读取失败而中断。

#### AI 检查逻辑

提取文本后，系统构建项目上下文（项目名称、团队、材料要求、各文件提取文本），发送至 **DashScope qwen-turbo** 模型（temperature 0.1，max_tokens 2048），由 LLM 从以下维度评估：

- **完整性**：是否所有必交材料都已提交
- **内容相关性**：材料内容是否与项目背景和竞赛要求匹配
- **规范性**：文档格式、信息完整性是否达标

#### 检查结果

| 结果 | 含义 | 后续操作 |
|------|------|----------|
| `pass` | 材料完整，内容合规 | 可提交教师最终审核 |
| `warning` | 存在小问题，建议补充 | 查看 AI 建议，修改后重新上传 |
| `reject` | 材料缺失或重大不符 | 必须修改后重新上传 |

检查结果保存为 `review_record`，同时：
- 非通过结果自动生成 `notify_message` 通知项目队长
- 记录 `agent_task_log` 审计日志（工具名、输入摘要、结果摘要、执行状态）

#### 降级保障

当 LLM API 不可达时，返回退化结果（`warning` + "AI 检查暂时不可用"），确保系统主流程不中断。

---

### 3.6 教师审核

#### 项目列表视图

教师登录后看到专用的仪表盘，展示所有与其相关的项目：

- 作为指导老师参与的项目
- 项目状态标签（draft / incomplete / ready / reviewed）
- 完成率进度条
- 材料提交/审核统计（已提交数 / 已通过数 / 需修改数）
- 团队成员标签

点击项目可进入审核面板。

#### 逐项审核

教师对每个材料项进行独立审核：

| 操作 | 说明 |
|------|------|
| **通过**（approved） | 标记材料审核通过，无需修改 |
| **需修改**（revision） | 填写修改建议（`review_comment`），通知学生重新提交 |
| **重置审核**（reset） | 撤销之前的审核结果，恢复为未审核状态 |

审核权限控制：只有指导老师（advisor 角色成员）和管理员可以审核。

#### 审核反馈机制

当教师选择"需修改"时，系统自动：
1. 更新 `project_material.review_status` = `revision`
2. 保存 `review_comment` 修改建议
3. 生成 `notify_message` 发送给项目队长

学生可在材料检查面板看到审核状态和修改建议。

---

### 3.7 系统管理

管理员通过独立的管理面板执行用户管理操作：

| 操作 | 说明 |
|------|------|
| **用户列表** | 支持关键词搜索（用户名/真实姓名），分页展示 |
| **创建用户** | 填写用户名、真实姓名、密码、角色，由服务端创建 |
| **编辑角色** | 修改用户角色（student / teacher / admin） |
| **删除用户** | 逻辑删除（设置 `is_deleted = 1`），数据不物理删除 |

所有管理操作需要 admin 角色权限。

---

### 3.8 消息中心

系统在关键事件发生时自动生成通知消息，前端提供完整的消息中心 UI：

| 触发事件 | 消息类型 | 接收人 |
|----------|----------|--------|
| AI 材料检查不通过 | `material` | 项目队长 |
| 教师审核"需修改" | `material` | 项目队长 |
| 截止日期临近 | `deadline` | 项目队长 |

消息字段：
- `msg_type`：消息类型（deadline / material / system）
- `msg_content`：消息正文（最长 500 字符）
- `is_read`：是否已读标记

**前端消息中心功能：**
- 侧边栏「消息中心」导航项，带红色未读数字 Badge
- 消息列表：按时间倒序展示，区分已读/未读样式
- 类型标签：材料通知（橙色）、截止提醒（红色）、系统消息（蓝色）
- 分段筛选：全部消息 / 仅未读
- 单条标为已读（点击）或一键全部已读
- 点击未读消息自动标记已读

**API 端点：**
- `GET /notify/messages` — 获取消息列表（支持 isRead 筛选）
- `GET /notify/unread-count` — 获取未读数量
- `PUT /notify/{msgId}/read` — 标记单条已读
- `PUT /notify/read-all` — 全部标记已读

---

### 3.9 审计日志

系统自动记录每次 AI 工具调用的完整轨迹，前端提供可查询的审计日志面板：

**记录字段：**
| 字段 | 说明 |
|------|------|
| `task_id` | 任务 ID（自增主键） |
| `project_id` | 关联项目 ID（可为空） |
| `tool_name` | 工具名称（`parseNoticeTool` / `checkMaterialTool`） |
| `input_summary` | 输入摘要（如通知标题、材料数量） |
| `result_summary` | 结果摘要（AI 返回内容截取前 200 字符） |
| `execute_status` | 执行状态（`success` / `failed`） |
| `created_at` | 执行时间 |

**前端审计日志功能：**
- 侧边栏「审计日志」导航项
- 表格展示：任务 ID、项目 ID、工具名称（中文映射）、输入摘要、执行状态、时间
- 按工具名称筛选（通知解析 / 材料检查）
- 点击行展开查看完整输入摘要和执行结果
- 执行状态使用 StatusTag 展示

**API 端点：**
- `GET /agent/task-logs?projectId=&toolName=` — 查询审计日志（支持按项目/工具筛选）

### 3.10 文件管理

#### 存储方式

当前文件以 `LONGBLOB` 形式直接存储在 MySQL 的 `file_asset` 表中。表结构已预留 `storage_path` 字段用于未来迁移至 **MinIO / S3** 对象存储。

#### 文件分类

| 业务类型（biz_type） | 说明 |
|---------------------|------|
| `notice` | 竞赛通知文件 |
| `material` | 申报材料文件 |
| `other` | 其他文件 |

#### 文件操作

| 操作 | 说明 |
|------|------|
| 上传 | 配合通知/材料上传，表单中包含文件字段（multipart/form-data），最大 50MB |
| 下载 | GET `/file/{fileId}/download`，根据 MIME 类型自适应：PDF → 内联显示，其他 → 触发下载 |
| 预览 | 前端 `FileContentViewer.vue` 通过 Blob URL 在新标签页展开文件 |

---

## 四、技术架构

### 4.1 整体架构

```
┌────────────────────────────────────────────────────────────┐
│                        客户端层                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Vue 3.5 SPA (Vite 8 + Element Plus 2.13)     │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐  │  │
│  │  │ 通知面板  │ │ 项目面板  │ │ 材料面板  │ │ 管理面板│  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────┘  │  │
│  │           Axios HTTP Client (60s timeout)            │  │
│  │           Mock 模式 (VITE_USE_MOCK=true)             │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────┬─────────────────────────────────────┘
                       │ HTTP/REST (JWT Bearer Token)
┌──────────────────────▼─────────────────────────────────────┐
│                       服务端层                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │       Spring Boot 3.5.13 (Java 21, Maven)           │  │
│  │                                                      │  │
│  │  ┌──────────┐ ┌──────────┐ ┌────────────────────┐   │  │
│  │  │Controller│ │ Service  │ │  Common            │   │  │
│  │  │(9个)    │ │ (7个)    │ │  ApiResponse       │   │  │
│  │  │          │ │          │ │  FileTextExtractor │   │  │
│  │  └──────────┘ └──────────┘ │  PdfOcrExtractor   │   │  │
│  │                            └────────────────────┘   │  │
│  │  ┌──────────┐ ┌──────────┐ ┌────────────────────┐   │  │
│  │  │ MyBatis- │ │ Flyway   │ │ Security            │   │  │
│  │  │ Plus 3.5 │ │ Migration│ │ JWT (JJWT 0.12.6)   │   │  │
│  │  └──────────┘ └──────────┘ └────────────────────┘   │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────┬──────────────────────────────┬──────────────────┘
           │ MySQL/JDBC                   │ HTTP (RestTemplate)
           ▼                              ▼
┌──────────────────────┐  ┌──────────────────────────────────┐
│     数据层            │  │        AI 服务层                  │
│                      │  │                                  │
│  MySQL 8.0+          │  │  DashScope (OpenAI 兼容)          │
│  (Aiven / TiDB)      │  │  ┌────────────────────────────┐  │
│  • 10 张表           │  │  │ qwen-turbo (文本)          │  │
│  • 2 存储过程        │  │  │ • 通知解析 (parseNotice)   │  │
│  • 2 触发器          │  │  │ • 材料检查 (checkMaterial) │  │
│  • 3 视图            │  │  └────────────────────────────┘  │
│  • Flyway 迁移        │  │  ┌────────────────────────────┐  │
│                      │  │  │ qwen-vl-plus (视觉)        │  │
│                      │  │  │ • PDF OCR 文字提取          │  │
│                      │  │  └────────────────────────────┘  │
└──────────────────────┘  └──────────────────────────────────┘
```

### 4.2 技术栈明细

#### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 运行环境 |
| Spring Boot | 3.5.13 | 应用框架（web、validation） |
| MyBatis-Plus | 3.5.7 | ORM 框架，逻辑删除（`@TableLogic`） |
| Flyway | — | 数据库版本迁移管理 |
| MySQL Connector | — | 数据库驱动（mysql-connector-j） |
| Apache Tika | 3.1.0 | 文档文本提取（DOCX/PDF/XLSX） |
| Apache PDFBox | — | PDF 页面渲染（OCR 预处理） |
| JJWT | 0.12.6 | JWT Token 生成与验证（HMAC-SHA256） |
| Lombok | — | 简化实体和 DTO 代码 |
| Spring Security | 6.x | BCrypt 密码加密、安全过滤器链 |

#### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.32 | 前端框架（Composition API + `<script setup>`） |
| Vite | 8.0.8 | 构建工具与开发服务器 |
| Element Plus | 2.13.7 | UI 组件库（Table、Form、Dialog、Select 等） |
| Axios | 1.15.2 | HTTP 客户端（60 秒超时） |
| SCSS/Sass | 1.99.0 | CSS 预处理器 |
| Lucide Vue Next | 1.0.0 | 图标库 |

#### AI 服务

| 服务 | 模型 | 用途 |
|------|------|------|
| DashScope（阿里云） | `qwen-turbo` | 通知解析（temperature 0.1, max_tokens 2048）、材料检查 |
| DashScope（阿里云） | `qwen-vl-plus` | PDF OCR 视觉识别（max_tokens 4096） |
| Apache Tika | `AutoDetectParser` | 通用文档文本提取 |
| Apache PDFBox | `PDFRenderer` | PDF 转图片（JPEG, 100 DPI） |

#### 基础设施

| 技术 | 用途 |
|------|------|
| Docker | 多阶段构建（Node 22 → Maven 3.9 + JDK 21 → JRE 21） |
| Render | 云端部署平台（免费计划，512MB RAM） |
| Aiven MySQL | 免费云端数据库（5GB，MySQL 8.0 兼容） |
| Flyway | 数据库 schema 自动迁移 |

### 4.3 数据库设计

#### 表结构总览（10 张表）

```
sys_user ──────────────────────────────────────────────┐
  │ user_id (PK)                                       │
  │ username, password, real_name, role, phone          │
  └────────────────┬───────────────────────────────────┘
                   │
    ┌──────────────┼──────────────────────────────┐
    │              │                              │
    ▼              ▼                              ▼
file_asset    competition_notice           competition_project
  │ file_id      │ notice_id (PK)            │ project_id (PK)
  │ biz_type     │ title, organizer          │ notice_id (FK)
  │ file_blob    │ deadline, target_group    │ leader_id (FK→sys_user)
  │ uploaded_by  │ raw_text, ai_summary      │ status, deadline
  └──────┬───────┘ │ notice_file_id (FK)      │ completion_rate
         │         └──────────┬───────────────┘ └──────┬──────────────┘
         │                    │                        │
         │                    ▼                        │
         │         material_requirement                │
         │           │ requirement_id (PK)             │
         │           │ notice_id (FK)                  │
         │           │ requirement_name, is_required   │
         │           └──────────┬──────────────────────┘
         │                      │
         │   ┌──────────────────┘
         │   │
         ▼   ▼
    project_material ──────────────────┐
      │ material_id (PK)               │
      │ project_id (FK)                │
      │ requirement_id (FK)            │
      │ file_id (FK→file_asset)        │
      │ submit_status, version_no      │
      │ review_status, review_comment  │
      │ reviewed_by (FK→sys_user)      │
      └────────────────────────────────┘

    review_record          notify_message         agent_task_log
      │ review_id (PK)       │ msg_id (PK)          │ task_id (PK)
      │ project_id (FK)      │ project_id (FK)      │ project_id (FK)
      │ reviewer_id (FK)     │ receiver_id (FK)     │ tool_name
      │ review_type          │ msg_type             │ input_summary
      │ review_result        │ msg_content          │ result_summary
      │ review_comment       │ is_read              │ execute_status
      └──────────────────────┘ └────────────────────┘ └─────────────────────┘

    project_member
      │ member_id (PK)
      │ project_id (FK→competition_project)
      │ user_id (FK→sys_user)
      │ member_role (leader/member/advisor)
      └──────────────────────┘
```

#### 关联关系

| 父表 | 子表 | 关联字段 | 约束 |
|------|------|----------|------|
| sys_user | file_asset | uploaded_by → user_id | FK |
| sys_user | competition_project | leader_id → user_id | FK |
| sys_user | project_member | user_id → user_id | FK |
| sys_user | project_material | reviewed_by → user_id | FK |
| sys_user | review_record | reviewer_id → user_id | FK |
| sys_user | notify_message | receiver_id → user_id | FK |
| competition_notice | competition_project | notice_id → notice_id | FK |
| competition_notice | material_requirement | notice_id → notice_id | FK |
| competition_project | project_member | project_id → project_id | FK |
| competition_project | project_material | project_id → project_id | FK |
| competition_project | review_record | project_id → project_id | FK |
| material_requirement | project_material | requirement_id → requirement_id | FK |
| file_asset | project_material | file_id → file_id | FK |

#### 存储过程

| 存储过程 | 用途 |
|----------|------|
| `sp_refresh_project_progress(project_id)` | 重算项目完成率和状态 |
| `sp_project_material_summary(project_id)` | 返回项目材料提交汇总 |

#### 触发器

| 触发器 | 触发时机 | 行为 |
|--------|----------|------|
| `trg_project_material_after_insert_refresh_project` | 材料 INSERT 后 | 调用 `sp_refresh_project_progress` |
| `trg_project_material_after_update_refresh_project` | 材料 UPDATE 后 | 调用 `sp_refresh_project_progress`；若 project_id 变更，同时刷新旧项目 |

#### 视图

| 视图 | 说明 |
|------|------|
| `v_project_progress` | 项目进度总览（含队长姓名、通知标题） |
| `v_notice_material_summary` | 通知材料要求汇总 |
| `v_project_material_detail` | 项目材料最新版本详情（含文件名） |

#### 逻辑删除

全部 10 张表均包含 `is_deleted` 字段（TINYINT，默认 0），通过 MyBatis-Plus `@TableLogic` 注解实现。删除操作仅标记为 1，数据物理保留。

### 4.4 AI 集成方案

#### DashScope API 配置

```
Base URL: https://dashscope.aliyuncs.com/compatible-mode/v1
API Key: 环境变量 DASHSCOPE_API_KEY
接口风格: OpenAI Chat Completions 兼容
```

#### 两个 AI 工具

| 工具 | 端点触发 | 模型 | 输入 | 输出 |
|------|----------|------|------|------|
| parseNoticeTool | POST /notice/parse/{id} | qwen-turbo | 通知文本（≤4000 字符）+ 表单提示 | 结构化 JSON（主办方、截止日期、目标群体、关键点、材料清单） |
| checkMaterialTool | POST /agent/check-material/{id} | qwen-turbo | 项目上下文 + 各文件 Tika 提取文本 | 结构化 JSON（审核结果 pass/warning/reject + 详细中文评审意见） |

#### PDF 文本提取管线

```
文件上传
   │
   ▼
FileTextExtractor.extractText()
   │
   ├── 非 PDF 文件 → Tika AutoDetectParser → 返回文本
   │
   └── PDF 文件
        │
        ▼
      Tika AutoDetectParser
        │
        ├── 提取成功 → 返回文本
        │
        └── 提取失败/空内容
             │
             ▼
           PdfOcrExtractor.ocrPdf()
             │
             ├── PDFBox 渲染每页为 JPEG (100 DPI, max 3页, max 1200px)
             │
             ├── 并行发送至 qwen-vl-plus (CompletableFuture, 120s timeout/page)
             │
             └── 合并所有页面 OCR 结果 → 返回文本
```

### 4.5 部署架构

```
┌───────────────────────────────────────────────────────┐
│                     Render Cloud                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │              Docker Container                     │  │
│  │  ┌───────────────────┐  ┌────────────────────┐  │  │
│  │  │   Spring Boot JAR │  │  Vue Static Files  │  │  │
│  │  │   (Port 8080)     │  │  (hosted by SB)    │  │  │
│  │  └─────────┬─────────┘  └────────────────────┘  │  │
│  └────────────┼────────────────────────────────────┘  │
│               │                                        │
└───────────────┼────────────────────────────────────────┘
                │ SSL (REQUIRED)
                ▼
┌───────────────────────────────────────────────────────┐
│                 Aiven MySQL (Free Tier)                │
│                  5GB, MySQL 8.0                        │
│                  ai_competition_db                      │
└───────────────────────────────────────────────────────┘
```

Docker 镜像通过三阶段构建：
1. **Stage 1（Node 22 Alpine）**：`npm install && npm run build` → 生成 `frontend/dist/`
2. **Stage 2（Maven 3.9 + JDK 21）**：`mvnw clean package -DskipTests` → 生成 JAR（含前端静态文件）
3. **Stage 3（JRE 21 Alpine）**：复制 JAR，`-Xmx512m` 运行

---

## 五、API 参考

### 5.1 通用说明

**Base URL**: `http://localhost:8080`（本地）/ Render 分配域名（生产）

**认证方式**: JWT Bearer Token（`Authorization: Bearer <token>`）

**通用响应格式**（`ApiResponse<T>`）：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": "2026-06-18T10:30:00"
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 业务错误（参数校验失败、权限不足等） |
| 500 | 系统错误（未捕获异常） |

### 5.2 认证模块 — `/auth`

| 方法 | 路径 | 请求体 | 响应 data | 说明 |
|------|------|--------|-----------|------|
| POST | `/auth/login` | `{username, password}` | `{token, userId, username, realName, role}` | 用户登录 |
| POST | `/auth/register` | `{username, password, confirmPassword, realName}` | — | 用户注册（默认 student 角色） |

### 5.3 仪表盘 — `/dashboard`

| 方法 | 路径 | 响应 data | 说明 |
|------|------|-----------|------|
| GET | `/dashboard/bootstrap` | `{latestNotice, notices, users, userProject}` | 聚合初始化数据（通知列表、用户列表、当前用户最新项目详情+进度+AI审核） |

### 5.4 通知管理 — `/notice`

| 方法 | 路径 | 请求体/参数 | 响应 data | 说明 |
|------|------|-------------|-----------|------|
| POST | `/notice/upload` | multipart: `file`, `title`, `organizer`, `deadline`, `targetGroup`, `rawText`, `createdBy` | `{noticeId, title}` | 上传通知文件+元数据 |
| POST | `/notice/parse/{noticeId}` | — | `{organizer, deadline, targetGroup, aiSummary, materialRequirements}` | AI 解析通知 |

### 5.5 项目管理 — `/project`

| 方法 | 路径 | 请求体/参数 | 响应 data | 说明 |
|------|------|-------------|-----------|------|
| POST | `/project/create` | JSON: `{noticeId, projectName, teamName, leaderId, members[], deadline}` | `{projectId}` | 创建项目（含成员和材料初始化） |
| GET | `/project/detail/{projectId}` | — | `{project, members[], materials[], reviewRecords[]}` | 项目详情 |
| GET | `/project/progress/{projectId}` | — | `{completionRate, requiredTotal, requiredSubmitted, requiredMissing}` | 项目进度 |
| GET | `/project/my-projects` | — | `[{projectId, projectName, status, ...}]` | 当前用户参与的项目列表 |
| GET | `/project/{projectId}/review-status` | — | `[{materialId, requirementName, reviewStatus, reviewComment, ...}]` | 材料审核状态 |
| POST | `/project/{projectId}/members` | `{userId, memberRole}` | — | 添加成员 |
| DELETE | `/project/{projectId}/members/{memberId}` | — | — | 移除成员 |

### 5.6 材料管理 — `/material`

| 方法 | 路径 | 请求体/参数 | 响应 data | 说明 |
|------|------|-------------|-----------|------|
| POST | `/material/upload` | multipart: `file`, `projectId`, `requirementId`, `uploadedBy`, `remark` | `{materialId, versionNo}` | 上传材料（自动版本管理） |
| POST | `/material/review` | `{materialId, reviewStatus, reviewComment, reviewedBy}` | — | 教师审核材料（approved/revision） |
| POST | `/material/{materialId}/reset-review` | — | — | 重置审核状态 |

### 5.7 AI 检查 — `/agent`

| 方法 | 路径 | 参数 | 响应 data | 说明 |
|------|------|------|-----------|------|
| POST | `/agent/check-material/{projectId}` | — | `{reviewResult, reviewComment}` | 运行 AI 材料检查 |
| GET | `/agent/task-logs` | `?projectId=&toolName=` | `[{taskId, projectId, toolName, inputSummary, resultSummary, executeStatus, createdAt}]` | 查询审计日志 |

### 5.8 通知消息 — `/notify`

| 方法 | 路径 | 参数 | 响应 data | 说明 |
|------|------|------|-----------|------|
| GET | `/notify/messages` | `?receiverId=&isRead=` | `[{msgId, projectId, msgType, msgContent, isRead, createdAt}]` | 获取消息列表 |
| GET | `/notify/unread-count` | `?receiverId=` | `number` | 获取未读消息数 |
| PUT | `/notify/{msgId}/read` | — | — | 标记单条已读 |
| PUT | `/notify/read-all` | `?receiverId=` | — | 全部标记已读 |

### 5.9 文件管理 — `/file`

| 方法 | 路径 | 响应 | 说明 |
|------|------|------|------|
| GET | `/file/{fileId}/download` | 文件二进制流（Content-Type 自适应） | 下载/预览文件 |

### 5.10 用户管理 — `/user`

| 方法 | 路径 | 请求体 | 响应 data | 说明 |
|------|------|--------|-----------|------|
| GET | `/user/profile` | — | `{userId, username, realName, role, phone}` | 获取当前用户资料（需 JWT） |
| PUT | `/user/profile` | `{username, realName, phone}` | — | 更新个人资料 |
| PUT | `/user/change-password` | `{oldPassword, newPassword, confirmPassword}` | — | 修改密码 |

### 5.11 管理员 — `/admin`

| 方法 | 路径 | 请求体/参数 | 响应 data | 说明 |
|------|------|-------------|-----------|------|
| GET | `/admin/users?keyword=` | query: `keyword`（可选） | `[{userId, username, realName, role, phone, ...}]` | 用户列表 |
| POST | `/admin/users` | `{username, password, realName, role}` | `{userId}` | 创建用户 |
| PUT | `/admin/users/{userId}/role` | `{role}` | — | 修改用户角色 |
| DELETE | `/admin/users/{userId}` | — | — | 删除用户（逻辑删除） |

---

## 六、部署运维

### 6.1 环境要求

| 依赖 | 版本要求 |
|------|----------|
| JDK | 21+（[Eclipse Adoptium](https://adoptium.net/) 推荐） |
| Node.js | ≥ 20.19.0 或 ≥ 22.12.0 |
| MySQL | 8.0+ |
| Maven | 项目内置 Maven Wrapper，无需手动安装 |

### 6.2 本地开发

#### 1. 准备数据库

```sql
CREATE DATABASE IF NOT EXISTS ai_competition_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

默认连接：`root:root@localhost:3306/ai_competition_db`

#### 2. 环境变量（可选覆盖）

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `DB_HOST` | `localhost` | 数据库地址 |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_NAME` | `ai_competition_db` | 数据库名 |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `root` | 数据库密码 |
| `DB_SSL_MODE` | — | SSL 模式（生产环境设为 `REQUIRED`） |
| `JWT_SECRET` | （内置默认值） | JWT 签名密钥，生产环境必须修改 |
| `DASHSCOPE_API_KEY` | — | DashScope API Key（可选，未配置时 AI 降级） |

#### 3. 启动后端

```bash
cd backend

# Windows PowerShell
$env:JAVA_HOME = "C:/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot"
./mvnw spring-boot:run
```

后端启动后 Flyway 自动执行数据库迁移（建表、触发器、存储过程、视图、演示数据）。

#### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`，API 请求指向 `http://localhost:8080`。

### 6.3 Mock 模式

前端支持完全离线的 Mock 开发模式，无需启动后端和数据库：

```env
# frontend/.env
VITE_USE_MOCK=true
```

启用后，所有 API 调用使用内存模拟数据（`src/mock/` 目录下的 3 个 Mock 服务），支持全部业务功能验证。Mock 数据包含 2 个演示项目、完整的审核记录和通知。

### 6.4 Docker 部署

```bash
# 构建镜像（三阶段构建，约 3-5 分钟）
docker build -t ai-competition .

# 运行容器
docker run -p 8080:8080 \
  -e DB_HOST=your-db-host \
  -e DB_PORT=20969 \
  -e DB_NAME=defaultdb \
  -e DB_USERNAME=avnadmin \
  -e DB_PASSWORD=your-password \
  -e DB_SSL_MODE=REQUIRED \
  -e JWT_SECRET=your-random-secret \
  -e DASHSCOPE_API_KEY=your-api-key \
  ai-competition
```

### 6.5 免费云端部署（Render + Aiven MySQL）

**总计费用：$0/月**

#### 步骤一：准备 Aiven MySQL

1. 注册 [aiven.io](https://aiven.io)
2. 创建 Free tier MySQL 服务（5GB 免费）
3. 获取连接信息（Host、Port、User、Password）
4. 在 Aiven 控制台创建数据库 `ai_competition_db`

#### 步骤二：部署到 Render

1. 将项目推送到 GitHub 仓库
2. 登录 [render.com](https://render.com)，创建 Web Service
3. 连接 GitHub 仓库，Render 自动识别 `render.yaml`
4. 配置环境变量：

| 环境变量 | 示例值 |
|----------|--------|
| `DB_HOST` | `xxx.aivencloud.com` |
| `DB_PORT` | `20969` |
| `DB_NAME` | `defaultdb` 或自定义 |
| `DB_USERNAME` | `avnadmin` |
| `DB_PASSWORD` | 你的 Aiven 密码 |
| `DB_SSL_MODE` | `REQUIRED` |
| `JWT_SECRET` | 随机字符串（Render 可自动生成） |
| `DASHSCOPE_API_KEY` | `sk-xxx`（可选） |

5. 点击创建，等待构建（约 5-10 分钟）

#### 防止休眠

Render 免费计划在 15 分钟无请求后休眠。推荐使用 [UptimeRobot](https://uptimerobot.com)（免费）每 5 分钟 ping 一次你的 Render URL，保持服务常驻。

### 6.6 演示账号

系统初始化后预置 3 个演示用户（密码均为 `123456`，BCrypt 加密）：

| 用户名 | 真实姓名 | 角色 |
|--------|----------|------|
| `admin` | 系统管理员 | admin |
| `teacher01` | 张老师 | teacher |
| `student01` | 李同学 | student |

---

## 七、开发指南

### 7.1 项目结构

```
aicompetition/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/eliza/aicompetition/
│   │   ├── AicompetitionApplication.java   # 启动入口（@SpringBootApplication + @MapperScan）
│   │   ├── common/             # 公共组件
│   │   │   ├── ApiResponse.java           # 统一响应信封（code/message/data/timestamp）
│   │   │   ├── FileTextExtractor.java     # Tika 文本提取（100K 字符限制，永不抛异常）
│   │   │   └── PdfOcrExtractor.java       # PDF OCR 管线（PDFBox + qwen-vl-plus 并行调用）
│   │   ├── config/             # 配置类
│   │   │   ├── LlmProperties.java         # @ConfigurationProperties("llm.api")
│   │   │   ├── LlmConfig.java             # RestTemplate Bean（30s 连接 / 120s 读取超时）
│   │   │   ├── SecurityConfig.java        # Spring Security（BCrypt, 全部 permitAll）
│   │   │   └── WebMvcConfig.java          # CORS 配置（允许所有来源）
│   │   ├── controller/         # 8 个 REST 控制器
│   │   ├── service/            # 7 个业务服务类
│   │   │   ├── AiService.java             # LLM 客户端（提示词构建、JSON 清洗、降级处理）
│   │   │   ├── AgentService.java          # AI 材料检查编排 + 审计日志查询
│   │   │   ├── AuthService.java           # 登录/注册
│   │   │   ├── MaterialService.java       # 材料上传/审核
│   │   │   ├── NoticeService.java         # 通知管理
│   │   │   ├── NotifyService.java         # 消息管理（列表/未读数/已读标记）
│   │   │   └── ProjectService.java        # 项目管理
│   │   ├── entity/             # 10 个数据库实体（全部 @Data + @TableLogic）
│   │   ├── dto/                # 请求/响应 DTO
│   │   ├── mapper/             # MyBatis-Plus Mapper 接口 + 自定义 SQL XML
│   │   ├── exception/          # BusinessException + GlobalExceptionHandler
│   │   └── util/               # JwtUtil（JJWT HMAC-SHA 签名）
│   ├── src/main/resources/
│   │   ├── db/
│   │   │   ├── bootstrap/00_create_database.sql
│   │   │   ├── migration/V1__init_schema.sql    # 完整 schema（表+触发器+存储过程+视图+演示数据）
│   │   │   └── verification/V4__verification_queries.sql
│   │   ├── mapper/             # MyBatis XML（3 个自定义查询）
│   │   ├── application.properties       # 主配置
│   │   └── application-prod.properties  # 生产环境配置
│   └── pom.xml
│
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── api/
│   │   │   ├── client.js              # Axios 实例 + JWT 拦截器 + Mock 开关
│   │   │   ├── auth.js                # 登录/注册 API
│   │   │   ├── competition.js         # 全部业务 API（通知/项目/材料/AI/审核/文件）
│   │   │   └── user.js                # 用户管理 API
│   │   ├── components/
│   │   │   ├── auth/                  # LoginPage, RegisterPage
│   │   │   ├── admin/                 # AdminDashboard（用户 CRUD）
│   │   │   ├── dashboard/             # 7 个业务面板
│   │   │   │   ├── NoticeUploadPanel.vue        # 通知上传 + AI 解析
│   │   │   │   ├── ProjectCreatePanel.vue       # 项目创建 + 成员管理
│   │   │   │   ├── MaterialCheckPanel.vue       # 材料上传 + AI 检查（学生）
│   │   │   │   ├── MaterialReviewPanel.vue      # 材料审核（教师）
│   │   │   │   ├── TeacherDashboard.vue         # 教师项目列表
│   │   │   │   ├── MessageCenterPanel.vue       # 消息中心（通知列表+已读管理）
│   │   │   │   └── AgentTaskLogPanel.vue        # 审计日志（AI 调用记录查询）
│   │   │   ├── layout/               # AppShell（侧边栏 + 顶栏 + 内容区）
│   │   │   ├── shared/               # FeaturePanel, StatCard, StatusTag, FileContentViewer
│   │   │   └── user/                 # UserProfileDialog
│   │   ├── mock/                     # 3 个完整内存 Mock 服务
│   │   │   ├── authService.js
│   │   │   ├── competitionService.js   # 800+ 行，完整业务模拟
│   │   │   └── userService.js
│   │   ├── utils/                    # format.js（日期格式化）, status.js（状态映射）
│   │   ├── styles/main.scss          # 全局样式 + Element Plus 覆盖
│   │   ├── App.vue                   # 根组件（状态管理中心）
│   │   └── main.js                   # 入口（挂载 Vue + Element Plus）
│   ├── .env / .env.production        # 环境变量
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
│
├── Dockerfile                  # 多阶段 Docker 构建
├── render.yaml                 # Render 部署配置
├── README.md                   # 快速入门
└── docs/PRODUCT.md             # 本产品文档
```

### 7.2 关键设计模式

#### 1. 统一响应信封

所有控制器返回 `ApiResponse<T>`，包含 `code`、`message`、`data`、`timestamp`。前端无需处理各种响应格式。

#### 2. 状态管理

前端不使用 Vuex/Pinia，改为根组件 `App.vue` 通过 `reactive()/ref()` 集中管理状态，子组件通过 props 接收数据，通过 emit 事件向上传递操作。

#### 3. Mock 双轨架构

每个 API 函数在 `useMockApi` 标志下分为真实调用和 Mock 调用两个分支。Mock 服务是完整的内存数据库实现，与真实后端行为一致。

#### 4. AI 降级策略

所有 AI 调用均为 fail-safe：API 不可达时返回退化中文提示，不抛异常，不中断事务。

#### 5. 双重进度保障

项目进度同时由数据库触发器（实时）和 Java 服务方法（显式调用）更新，确保数据一致性。

#### 6. 逻辑删除

全部 10 张表通过 `is_deleted` 字段实现逻辑删除，MyBatis-Plus `@TableLogic` 自动在查询中过滤已删除记录。

### 7.3 扩展建议

| 扩展方向 | 说明 | 优先级 |
|----------|------|--------|
| 文件存储迁移 MinIO/S3 | `file_asset.storage_path` 已预留，需实现文件上传/下载适配 | 高 |
| Spring Security 强化 | 启用过滤器级认证拦截，替换当前手动 JWT 解析 | 中 |
| 单元测试覆盖 | 当前仅有 1 个 contextLoads 测试，需补充 Service/Controller 测试 | 中 |
| Pinia 状态管理 | 随面板增多，建议迁移到 Pinia 统一状态管理 | 低 |
| 异步任务队列 | AI 检查、文件解析等耗时操作可改为异步（RabbitMQ/Redis Queue） | 低 |

---

> **文档维护说明**：本文档反映 2026-06-19 时的系统状态（v1.1）。新增消息中心 UI、审计日志面板、侧边栏用户区、全局字号提升。功能实现以代码为准，如有出入请提交 Issue 或更新本文档。
