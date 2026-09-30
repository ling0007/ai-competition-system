# 三张核心工程图（绘图底稿）

以下节点以当前单体代码、配置和测试为依据。线条表示真实调用或数据依赖；其中任务恢复只按单实例语义描述。

## 1. 系统整体架构

```mermaid
flowchart LR
  U[管理员 / 学生 / 教师] --> V[Vue 3 前端<br/>角色路由与任务轮询]
  V --> C[Spring Boot REST API<br/>Security / JWT]
  C --> N[通知 / 项目 / 材料 / 审核 / 消息]
  C --> T[AI 任务服务与有界执行器]
  N <--> DB[(MySQL<br/>业务表 / 文件 LONGBLOB / 任务与结果)]
  T <--> DB
  N -. 全局展示缓存、上传短锁 .-> R[(Redis)]
  N --> F[文件提取<br/>Tika / PDFBox]
  T --> F
  F --> AI[DashScope<br/>视觉 OCR]
  T --> AI
```

- MySQL 是权限、业务状态和 AI 任务的事实来源；Redis 不负责 AI 任务排队或结果正确性。
- 通知解析结果进待确认草稿；材料 AI 初审与教师审核分开记录。文件当前存于 MySQL，没有对象存储服务。
- Compose 部署把构建后的 Vue 静态文件放进 Spring Boot 镜像；没有单独的前端运行容器。

## 2. AI 异步任务链路

```mermaid
flowchart TD
  A[POST 通知解析 / 材料检查] --> B[权限与业务状态校验]
  B --> C[短事务: 输入快照 + agent_task_log PENDING<br/>通知同时 CAS 到 PARSING]
  C --> D[返回 taskId]
  C --> E[提交后投递共享有界线程池]
  E --> F{worker CAS 领取成功?}
  F -- 是 --> G[RUNNING; 事务外提取文件 / OCR / 主模型]
  F -- 否 --> X[重复投递无效]
  E -- 队列拒绝 --> P[PENDING 留在 MySQL]
  P --> S[5 秒扫描重投或到期 TIMEOUT]
  S --> F
  G --> H{锁任务行并复核<br/>deadline / 状态 / 输入快照}
  H -- 有效 --> I[短事务提交草稿或 AI 初审 + SUCCESS<br/>result_origin = MODEL / FALLBACK]
  H -- 超时 --> J[TIMEOUT; 晚到结果不提交]
  H -- 输入变化或无可用结果 --> K[FAILED; 不提交当前结果]
  I --> O[终态后尽力写 taskId 观测]
  J --> O
  K --> O
  D --> Q[前端按 taskId 轮询授权查询 API]
  I --> Q
  J --> Q
  K --> Q
  I --> M[通知草稿由管理员确认 / 材料由教师复核]
```

- 通知默认 300 秒、材料默认 600 秒，从接受时计入排队；`PENDING` 可扫描恢复，进程重启留下的 `RUNNING` 收为失败或超时，需有权用户显式发起新尝试。
- `SUCCESS/MODEL` 表示主模型结果被提交，不能单独证明 PDF OCR 全页覆盖；观测中的页数与诊断码是另一维度。
- 模型调用失败时 `AiService` 返回确定性的待人工处理结果，任务可为 `SUCCESS/FALLBACK`。输入变化、总 deadline 到期或通知完全不可提取等路径没有可用结果，落 `FAILED/TIMEOUT`。终态结果写入与业务写入同事务，观测写入在其后尽力执行。

## 3. PDF OCR 与长文档处理

```mermaid
flowchart TD
  A[通知附件或材料文件字节] --> B[Tika 提取]
  B --> C{PDF 且无有效文本?}
  C -- 否 --> D[文本路径<br/>DOCX / TXT / 文本型 PDF 等]
  C -- 是 --> E[PDFBox 读取页数并校验<br/>20 MiB / 20 页 / 任务预算]
  E -- 超限 --> Z[明确不可用<br/>通知任务失败并提示提供文本;<br/>材料标 WARNING 待人工核对]
  E -- 合法 --> F[按页序每批渲染 2 页<br/>像素与 JPEG 字节上限]
  F --> G[最多 2 个视觉请求并发<br/>单页失败最多重试 1 次;<br/>总视觉调用上限 40]
  G --> H{页结果}
  H -- 成功 --> I[保留页号与 OCR 文本]
  H -- 单页失败 --> J[保留页号缺页占位<br/>OCR_PARTIAL / 人工核对]
  H -- 预算耗尽 --> Z
  D --> K[通知主解析输入]
  I --> K
  J --> K
  K --> L[按页 / 换行切 chunk<br/>每段最多 4000 字符 / 最多 8 段]
  L -- 超 8 段 --> N[INPUT_TOO_LONG<br/>确定性 fallback 草稿]
  L --> M[逐段主模型结构化解析]
  M -- 模型失败或非法响应 --> N
  M -- 有效 --> P[确定性合并<br/>字段冲突提示 / 截止时间冲突置空<br/>材料归一去重与来源页]
  P --> Q[首页明确正式标题可按原文恢复]
  Q --> R[待确认通知草稿]
  N --> R
```

- 第 3 图的 `chunk` 路径对应**通知结构化解析**；材料初审复用 Tika/OCR 提取与缺页提示，但使用自己的项目材料上下文和主模型检查，不走通知分段合并。
- `partial` 仅指在允许处理的页数内有单页失败且其他页可用；超页、超字节、预算耗尽或零可用页不能被画成“完整成功”。限时 future 不能保证底层 HTTP 立即终止，最终结果仍受第 2 图的任务终态保护。
- “deterministic fallback”是模型失败或通知过长时生成的可人工处理草稿；首页标题恢复是有原文证据时的确定性修正，两者含义不同。

## 绘图证据

[任务与扫描器](../../backend/src/main/java/com/eliza/aicompetition/service/NoticeAiTaskDispatcher.java) · [通知任务](../../backend/src/main/java/com/eliza/aicompetition/service/NoticeService.java) · [材料任务](../../backend/src/main/java/com/eliza/aicompetition/service/AgentService.java) · [提取路由](../../backend/src/main/java/com/eliza/aicompetition/common/FileTextExtractor.java) · [OCR 实现](../../backend/src/main/java/com/eliza/aicompetition/common/PdfOcrExtractor.java) · [分段与合并](../../backend/src/main/java/com/eliza/aicompetition/service/AiService.java) · [配置](../../backend/src/main/resources/application.properties)
