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
- **阿里云 DashScope**（OpenAI 兼容接口，模型 `qwen-turbo`）
- 通知智能解析、材料完整性检查

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
cd aicompetition

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
├── aicompetition/              # 后端 Spring Boot
│   ├── src/main/java/com/eliza/aicompetition/
│   │   ├── AicompetitionApplication.java
│   │   ├── common/             # ApiResponse、文件提取工具
│   │   ├── config/             # LLM 配置、WebMvc、Security
│   │   ├── controller/         # REST 控制器
│   │   ├── dto/                # 请求/响应 DTO
│   │   ├── entity/             # 数据库实体
│   │   ├── exception/          # 全局异常处理
│   │   ├── mapper/             # MyBatis-Plus Mapper
│   │   └── service/            # 业务逻辑 + AI 服务
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway 迁移脚本
│   │   ├── mapper/             # 自定义 SQL XML
│   │   └── application.properties
│   └── pom.xml
├── frontend/                   # 前端 Vue 3
│   ├── src/
│   │   ├── api/                # API 调用 + Axios 配置
│   │   ├── components/         # 组件
│   │   │   ├── auth/           # 登录/注册
│   │   │   ├── dashboard/      # 业务面板
│   │   │   ├── layout/         # 布局组件
│   │   │   └── shared/         # 公共组件
│   │   ├── mock/               # Mock 服务
│   │   ├── utils/              # 工具函数
│   │   ├── App.vue             # 根组件（状态管理入口）
│   │   └── main.js             # 入口
│   ├── .env                    # 环境变量
│   └── package.json
└── README.md
```

---

## 核心功能

### 竞赛通知管理
- 上传竞赛通知文件，自动提取文本
- AI 智能解析通知内容（主办方、截止日期、材料要求等）
- 结构化展示解析结果

### 项目申报
- 基于通知创建参赛项目
- 管理项目成员（队长/队员/指导老师）
- 跟踪项目整体状态（草稿 → 材料不全 → 就绪）

### 材料提交与版本管理
- 按通知要求逐项上传材料
- 材料版本追踪，支持重新提交
- 实时计算完成率

### AI 材料检查
- 提取上传文件文本（支持 DOCX/PDF/XLSX）
- AI 对比材料要求与项目背景进行智能核验
- 返回通过/驳回/警告的详细评审意见

### 教师审核（教师角色）
- 查看所有申报项目
- 逐项审核材料并提交审核结果
- 查看项目详情与成员信息

---

## 用户角色

| 角色 | 权限 |
|------|------|
| **学生** | 创建项目、上传材料、运行 AI 检查、查看进度 |
| **教师** | 查看所有项目、审核材料、管理项目成员 |
| **管理员** | 系统管理（待扩展） |

---

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 用户登录 |
| POST | `/auth/register` | 用户注册 |
| GET | `/dashboard/bootstrap` | 获取初始化数据 |
| POST | `/notice/upload` | 上传通知 |
| POST | `/notice/parse/{noticeId}` | AI 解析通知 |
| POST | `/project/create` | 创建项目 |
| GET | `/project/detail/{projectId}` | 项目详情 |
| GET | `/project/progress/{projectId}` | 项目进度 |
| POST | `/material/upload` | 上传材料 |
| POST | `/agent/check-material/{projectId}` | AI 材料检查 |

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
  -e DB_PORT=4000 \
  -e DB_NAME=ai_competition_db \
  -e DB_USERNAME=your-username \
  -e DB_PASSWORD=your-password \
  -e DB_SSL=true \
  -e DB_REQUIRE_SSL=true \
  -e JWT_SECRET=your-random-secret \
  -e DASHSCOPE_API_KEY=your-api-key \
  ai-competition
```

### 方式二：免费云端部署（Render + TiDB Cloud）

完全免费将项目部署到线上，总计 **$0/月**。

#### 1. 准备免费 MySQL 数据库

使用 [TiDB Cloud Serverless Tier](https://tidbcloud.com)（MySQL 兼容，5GB 永久免费）：

1. 注册并登录 [tidbcloud.com](https://tidbcloud.com)
2. 创建 **Serverless Tier** 集群
3. 在集群详情页 → **Connect** → 获取连接信息：
   - Host: `xxx.tidbcloud.com`
   - Port: `4000`
   - User: `xxx.root`
   - 设置密码并保存

> 备选：[Aiven MySQL](https://aiven.io)（免费 5GB）、[freedb.tech](https://freedb.tech)

#### 2. 部署到 Render

[Render](https://render.com) 免费计划支持 Docker 部署（512MB RAM，750h/月）。

1. 将项目推送到 GitHub 仓库
2. 登录 [render.com](https://render.com)，点击 **New → Web Service**
3. 连接 GitHub 仓库，Render 会自动识别 `render.yaml` 配置
4. 配置以下环境变量（`render.yaml` 中已预定义）：

| 环境变量 | 说明 | 示例 |
|---------|------|------|
| `DB_HOST` | TiDB Cloud 主机地址 | `xxx.tidbcloud.com` |
| `DB_PORT` | 数据库端口 | `4000` |
| `DB_NAME` | 数据库名 | `ai_competition_db` |
| `DB_USERNAME` | 数据库用户名 | `xxx.root` |
| `DB_PASSWORD` | 数据库密码 | 你设置的密码 |
| `DB_SSL` | 启用 SSL | `true` |
| `DB_REQUIRE_SSL` | 强制 SSL | `true` |
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
