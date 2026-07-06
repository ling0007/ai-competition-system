# AI 竞赛材料管理系统

基于 AI 的竞赛材料管理系统，帮助师生团队高效管理竞赛项目申报、材料提交与智能审核。

## 技术栈

### 后端
- **Java 21** + **Spring Boot 3.5.13**
- **MyBatis-Plus 3.5.7** — ORM 与逻辑删除
- **MySQL** + **Flyway** — 数据库与版本迁移
- **Apache Tika 3.1.0** — 文件文本提取（DOCX/PDF/XLSX）
- **JJWT 0.12.6** — JWT 认证
- **Lombok** — 简化代码

### 前端
- **Vue 3.5**（Composition API + `<script setup>`）
- **Vite 8** — 构建工具
- **Element Plus 2.13** — UI 组件库
- **Axios** — HTTP 请求
- **SCSS** — 样式

### AI 能力
- **阿里云 DashScope**（OpenAI 兼容接口）
  - 文本模型 `qwen-turbo` — 通知智能解析、材料完整性检查
  - 视觉模型 `qwen-vl-plus` — PDF OCR（扫描件/图片型 PDF 文字提取）
- **Apache Tika 3.1.0** — 文件文本提取（DOCX/PDF/XLSX）
- **Apache PDFBox** — PDF 页面渲染，配合视觉模型实现 OCR 管线

---

## 环境要求

- **JDK 21**（[Eclipse Adoptium](https://adoptium.net/) 推荐）
- **Node.js** ≥ 20.19
- **MySQL** 8.0+
- **Maven**（项目内置 Maven Wrapper，无需手动安装）

---

## 快速开始

### 1. 克隆项目

```bash
git clone <repo-url>
cd aicompetition
```

### 2. 准备数据库

在 MySQL 中创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS ai_competition_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

默认连接配置：`root:root@localhost:3306/ai_competition_db`。可通过环境变量覆盖：

| 环境变量 | 默认值 |
|---------|--------|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `ai_competition_db` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `root` |

### 3. 启动后端

```bash
cd backend

# Windows PowerShell
$env:JAVA_HOME = "C:/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot"

# 构建并运行
./mvnw spring-boot:run
```

后端默认运行在 `http://localhost:8080`。启动时 Flyway 会自动执行数据库迁移。

### 4. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认运行在 `http://localhost:5173`，API 请求代理至后端 `localhost:8080`。

### 5. 配置 AI 功能（可选）

设置 DashScope API Key 以启用 AI 解析和智能检查：

```bash
# Windows PowerShell
$env:DASHSCOPE_API_KEY = "your-api-key"

# Linux / macOS
export DASHSCOPE_API_KEY=your-api-key
```

未配置时，AI 功能将返回降级提示，不影响系统正常运行。

---

## 项目结构

```
aicompetition/                  # 项目根目录
├── backend/                    # 后端 Spring Boot
│   ├── src/main/java/com/eliza/aicompetition/
│   │   ├── AicompetitionApplication.java
│   │   ├── common/             # ApiResponse、文件提取工具、PDF OCR
│   │   ├── config/             # LLM 配置、WebMvc、Security
│   │   ├── controller/         # REST 控制器（8 个）
│   │   ├── dto/                # 请求/响应 DTO
│   │   ├── entity/             # 数据库实体（10 张表）
│   │   ├── exception/          # 全局异常处理
│   │   ├── mapper/             # MyBatis-Plus Mapper
│   │   ├── service/            # 业务逻辑 + AI 服务
│   │   └── util/               # JWT 工具
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway 迁移脚本（V1 完整 schema + 触发器/存储过程/视图）
│   │   ├── mapper/             # 自定义 SQL XML
│   │   └── application.properties
│   ├── pom.xml
│   └── CLAUDE.md               # 开发指南
├── frontend/                   # 前端 Vue 3
│   ├── src/
│   │   ├── api/                # API 调用 + Axios 配置
│   │   ├── components/         # 组件
│   │   │   ├── admin/          # 管理员面板
│   │   │   ├── auth/           # 登录/注册
│   │   │   ├── dashboard/      # 业务面板（通知、项目、材料、审核、进度）
│   │   │   ├── layout/         # 布局组件
│   │   │   ├── shared/         # 公共组件
│   │   │   └── user/           # 用户资料对话框
│   │   ├── mock/               # Mock 服务（3 个完整内存模拟）
│   │   ├── utils/              # 工具函数
│   │   ├── styles/             # SCSS 全局样式
│   │   ├── App.vue             # 根组件（状态管理入口）
│   │   └── main.js             # 入口
│   ├── .env                    # 环境变量
│   └── package.json
├── Dockerfile                  # 多阶段 Docker 构建
├── render.yaml                 # Render 云端部署配置
└── README.md
```

---

## 核心功能

### 竞赛通知管理
- 上传竞赛通知文件（支持 PDF/DOCX 等格式），自动提取文本
- AI 智能解析通知内容（主办方、截止日期、面向对象、材料要求等）
- 结构化展示解析结果

### 项目申报与成员管理
- 基于通知创建参赛项目
- 管理项目成员（队长/队员/指导老师），支持增删
- 跟踪项目整体状态（草稿 → 材料不全 → 就绪 → 已审核）
- 自动同步通知截止日期

### 材料提交与版本管理
- 按通知要求逐项上传材料
- 材料版本追踪（每次提交自动递增版本号，保留历史）
- 数据库触发器自动计算完成率与项目状态
- 查看每个要求的提交状态与历史版本

### AI 材料检查
- 提取上传文件文本（支持 DOCX/PDF/XLSX）
- PDF 两阶段提取：Tika 文本提取 → 失败时自动切换 PDFBox 渲染 + qwen-vl-plus 视觉 OCR
- AI 对比材料要求与项目背景进行智能核验
- 返回通过/警告/驳回的详细中文评审意见
- 自动生成通知消息，完整审计日志

### 教师审核
- 查看所有指导/参与项目，按状态筛选
- 逐项审核材料：通过 或 需修改（附修改建议）
- 审核意见实时反馈给学生
- 支持审核重置

### 通知消息
- AI 检查不通过时自动通知
- 教师打回材料时自动通知
- 支持消息类型：截止日期提醒 / 材料提醒 / 系统通知

### 用户与权限管理
- 三级角色（学生/教师/管理员），功能按角色区分
- 用户自助注册与登录
- 个人资料编辑、密码修改
- 管理员：用户 CRUD、角色分配

### 文件管理
- 文件上传（最大 50MB），当前存储为数据库 LONGBLOB（预留 MinIO/S3 扩展路径）
- 文件下载（根据 MIME 类型自适应：PDF 内联预览，其他类型下载）
- 业务分类（通知文件/材料文件/其他）

---

## 用户角色

| 角色 | 权限 |
|------|------|
| **学生** | 创建项目、管理成员、上传材料（支持多版本）、运行 AI 检查、查看进度 |
| **教师** | 查看所有项目、逐项审核材料（通过/需修改）、审核意见反馈、项目成员管理 |
| **管理员** | 用户管理（创建/编辑/删除）、角色分配、系统全局管理 |

---

## API 概览

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 用户登录 |
| POST | `/auth/register` | 用户注册 |

### 仪表盘
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dashboard/bootstrap` | 聚合初始化数据（通知列表、用户列表、当前用户最新项目详情+进度+AI审核） |

### 通知管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/notice/upload` | 上传通知文件 + 元数据 |
| POST | `/notice/parse/{noticeId}` | AI 解析通知内容 |

### 项目管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/project/create` | 创建参赛项目 |
| GET | `/project/detail/{projectId}` | 项目详情（含成员、材料、审核记录） |
| GET | `/project/progress/{projectId}` | 项目进度（完成率、缺失材料） |
| GET | `/project/my-projects` | 获取当前用户的项目列表 |
| GET | `/{projectId}/review-status` | 获取材料审核状态 |
| POST | `/{projectId}/members` | 添加项目成员 |
| DELETE | `/{projectId}/members/{memberId}` | 移除项目成员 |

### 材料管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/material/upload` | 上传材料文件（支持多版本） |
| POST | `/material/review` | 教师审核材料 |
| POST | `/{materialId}/reset-review` | 重置审核状态 |

### AI 检查
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agent/check-material/{projectId}` | 运行 AI 材料完整性检查 |

### 文件
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{fileId}/download` | 下载文件（PDF 内联显示，DOCX/XLSX 下载） |

### 用户管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/user/profile` | 获取当前用户资料 |
| PUT | `/user/profile` | 更新用户资料 |
| PUT | `/user/change-password` | 修改密码 |

### 管理员
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/users` | 用户列表（支持关键词搜索） |
| POST | `/admin/users` | 创建用户 |
| PUT | `/admin/users/{userId}/role` | 更新用户角色 |
| DELETE | `/admin/users/{userId}` | 删除用户（逻辑删除） |

---

## Mock 模式

前端支持离线开发，在 `frontend/.env` 中启用：

```env
VITE_USE_MOCK=true
```

启用后所有 API 调用将使用内存 Mock 数据，无需启动后端和数据库。

---

## 构建部署

### 方式一：Docker 一键部署（推荐）

项目根目录提供了多阶段 Dockerfile，构建产物包含前端和后端，可直接部署到任意支持 Docker 的平台。

```bash
# 本地构建 Docker 镜像
docker build -t ai-competition .

# 本地运行（需配置数据库环境变量）
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

### 方式二：免费云端部署（Render + Aiven MySQL）

完全免费将项目部署到线上，总计 **$0/月**。

#### 1. 准备免费 MySQL 数据库

使用 [Aiven MySQL](https://aiven.io)（免费 5GB，MySQL 8.0 兼容）：

1. 注册并登录 [aiven.io](https://aiven.io)
2. 创建 **Free tier** MySQL 服务
3. 在服务详情页获取连接信息：
   - Host: `xxx.aivencloud.com`
   - Port: `20969`
   - User: `avnadmin`
   - 数据库名: `defaultdb`
   - 设置密码并保存

> 备选：[TiDB Cloud Serverless Tier](https://tidbcloud.com)（MySQL 兼容，5GB 永久免费）、[freedb.tech](https://freedb.tech)

#### 2. 部署到 Render

[Render](https://render.com) 免费计划支持 Docker 部署（512MB RAM，750h/月）。

1. 将项目推送到 GitHub 仓库
2. 登录 [render.com](https://render.com)，点击 **New → Web Service**
3. 连接 GitHub 仓库，Render 会自动识别 `render.yaml` 配置
4. 配置以下环境变量（`render.yaml` 中已预定义）：

| 环境变量 | 说明 | 示例 |
|---------|------|------|
| `DB_HOST` | 数据库主机地址 | `xxx.aivencloud.com` |
| `DB_PORT` | 数据库端口 | `20969` |
| `DB_NAME` | 数据库名 | `defaultdb` |
| `DB_USERNAME` | 数据库用户名 | `avnadmin` |
| `DB_PASSWORD` | 数据库密码 | 你设置的密码 |
| `DB_SSL_MODE` | SSL 模式 | `REQUIRED` |
| `JWT_SECRET` | JWT 签名密钥 | 随机字符串（Render 可自动生成） |
| `DASHSCOPE_API_KEY` | AI 功能（可选） | `sk-xxx` |

5. 点击 **Create Web Service**，等待构建完成（约 5-10 分钟）
6. 首次启动时 Flyway 会自动创建数据库表结构

> **免费计划休眠说明**：Render 免费服务在 15 分钟无请求后会休眠，下次请求需等待约 30-60 秒冷启动。可使用 [UptimeRobot](https://uptimerobot.com)（免费）每 5 分钟 ping 一次避免休眠。

#### 3. 访问

部署完成后访问 Render 分配的 URL（如 `https://ai-competition.onrender.com`），即可使用系统。

### 方式三：本地构建

#### 后端

```bash
cd backend
./mvnw clean package -DskipTests
# 生成的 JAR 位于 target/aicompetition-0.0.1-SNAPSHOT.jar
java -jar target/aicompetition-0.0.1-SNAPSHOT.jar
```

#### 前端

```bash
cd frontend
npm run build
# 生成的静态文件位于 dist/
```

将 `dist/` 部署到任意静态服务器，或将前端构建产物复制到后端 `src/main/resources/static/` 由 Spring Boot 一并托管。
