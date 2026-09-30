# AGENTS.md — 工作协议与知识入口

## Source of truth

当前代码、测试和配置描述系统现在如何工作，是当前事实的最高权威。
[docs/knowledge/](docs/knowledge/index.md) 保存历史工程判断：设计理由、已验证的排查路径、系统关系和可复用做法，不保存聊天、任务流水账或容易从代码恢复的信息。
历史判断在重要决策前必须重新对照当前代码验证；`active` 也不例外。
代码描述事实，不自动授权改变人工制定的工程边界。

## 既有项目规范

完整规范保留在 [CLAUDE.md](CLAUDE.md)。原 AGENTS.md 与该文件除标题外内容相同，故这里引用既有文件，不复制第二套百科。
其中的工程规则仍然适用，不因本次导航整合而删除或弱化；事实描述、完成状态和示例须重新核对当前实现。
按任务范围阅读相关章节：后端分层 §2、前端 §3、数据库 §4、安全 §5、AI §6、状态 §7、接口与异常 §8–9、总禁令 §10、开发与验收 §11–12。安全相关 §5 和总禁令 §10 在业务改动前检查。
已有明确授权按授权范围执行；其他实施遵循既有先计划与确认流程。规范冲突先报告，不自行改写其含义。

## Before coding

每次实现非 trivial 任务之前：

1. 理解任务涉及的模块、文件、symbol 和领域概念。
2. 阅读 [知识索引](docs/knowledge/index.md)。
3. 找出最多 5 条与当前任务真正相关的 knowledge entries；可用 `python scripts/knowledge/search.py` 根据词项、路径、scope、role 搜索。
4. 只阅读相关条目，不默认加载整个 knowledge 目录；没有匹配时继续从代码探索。
5. 简要说明历史知识如何影响优先检查位置、优先验证的假设以及方案的历史风险。
6. 回到当前代码、测试、配置与 Git history 验证重要判断，区分已验证证据与推断。
7. 再形成实施计划，说明影响范围、文件改动、兼容性和必要验证，遵循当前用户授权。

Knowledge 用于缩小探索空间，不代替代码阅读。按相关范围查既有规范，不等于加载所有知识对象。

## During coding

历史知识与当前代码冲突时，不静默选择一方。明确记录并向用户说明：

- knowledge 说了什么（id 或原文链接）。
- 当前代码、测试或配置显示什么（路径与 symbol）。
- 为什么可能发生变化；未知原因明确标为未知。
- 是否应该更新知识，以及还缺哪些证据。

普通冲突在任务说明中报告；确有长期价值时更新原条目，未验证部分使用 `needs-review`。不为每个冲突创建日志。
constraint 冲突必须明确报告用户，等待人工决定规范修改；不能因为当前代码不同就自动放宽规则。

## After coding

任务完成、相关测试或验证通过后执行 **Knowledge Writeback Evaluation**，依次判断：

1. 是否产生未来很可能再次有用的工程判断？
2. 是否有当前代码、测试或实际任务结果作为证据？
3. 离开本次具体任务后能否独立成立？
4. 是否难以仅通过重新阅读几行代码恢复？
5. `docs/knowledge` 是否已有相同或高度相似的知识？

处理优先级严格为 **NO CHANGE > UPDATE EXISTING > CREATE NEW**。大部分任务应为 NO CHANGE，不能为“记录任务”创建知识。
有充分价值和证据才更新已有知识，确认无重复后才新建；不确定的结论提出建议，不写成事实。验证未通过或无法执行时如实说明，不假称通过。
写入使用 [模板](docs/knowledge/TEMPLATE.md)，提供证据、适用范围、重评条件，并同步索引。默认最多召回 5 条不代表必须创建或填满 5 条。

## Interview Writeback

本项目用于 Java 后端 / AI 应用实习面试。完成非 trivial 工程任务并通过验证后，除了 Knowledge Writeback Evaluation，还要执行一次 **Interview Value Evaluation**。

依次判断：

1. 本次改动是否解决了真实工程问题，而非单纯代码整理？
2. 是否体现明确的后端、数据库、中间件、安全、AI 工程或部署能力？
3. 面试官是否可能围绕这个设计继续追问？
4. 是否有当前代码、测试或运行结果作为证据？

如果大多数答案为否，结论为 **NO INTERVIEW WRITEBACK**。

如果具有明确面试价值，优先检查 `docs/interview/` 是否已有相同主题。处理优先级严格为 **NO CHANGE > UPDATE EXISTING > CREATE NEW**，不要按任务次数创建文档；一个工程主题只维护一篇长期文档。可使用的主题包括：

- `authentication-and-authorization`
- `status-and-material-consistency`
- `ai-human-confirmation`
- `redis-idempotency`
- `ai-async-task`
- `database-migration`
- `deployment`

面试文档不是 Source of Truth。当前代码、测试和配置仍然是最高权威。每篇文档必须基于实际实现和实际验证，不得把规划中的能力写成已经实现。

建议结构：

1. 原问题
2. 为什么值得解决
3. 实际设计
4. 核心调用链
5. 关键工程判断
6. 测试与验证
7. 实际遇到的问题 / 坑
8. 30 秒面试讲法
9. 2 分钟面试讲法
10. 高频追问与回答
11. 当前边界
12. Evidence

禁止：

- 编造没有实际发生的坑。
- 把未完成能力写成已实现。
- 复制任务流水账。
- 仅列修改文件。
- 为细小重构单独创建面试文档。

## Constraint policy

constraint 表示直接限制未来解决方案空间的工程边界，例如模块依赖禁令、安全数据边界、接口兼容承诺或架构层不可绕过的要求。

Agent 可以读取 constraint、发现它与当前事实冲突、提出新增或修改建议。
未经人工对具体内容明确批准，禁止：

- 新建 constraint。
- 删除 constraint。
- 改变、弱化 constraint 含义。
- 将 constraint 标为 deprecated。

上述保护同样适用于既有规范中的工程边界。不能改用 model、rationale 等 role 绕过审批；提出建议不构成批准。获批后保留可追溯的批准依据；本次搭建空 constraints 目录和模板不表示批准任何具体 constraint。

## 验证入口

以下命令应先与当前配置核对；不因文档修改默认启动全部服务：

- 后端：`backend/pom.xml` 定义 Java 21 / Spring Boot。Windows 在 `backend/` 用 `./mvnw.cmd test` 测试、`./mvnw.cmd package` 构建；其他平台用 `./mvnw`。`./mvnw.cmd "-Dtest=StatusStateMachineTests" test` 可单独检查枚举状态机；全量测试含 Spring 上下文测试，需相应环境。
- 前端：`frontend/package.json` 定义 Vue / Vite。在 `frontend/` 用 `npm run build` 构建；现有 scripts 未提供 test 或 lint。
- 文档与工具：当前未发现 Markdown lint 配置。检查链接、frontmatter、索引一致性和 `git diff --check`；脚本改动实际运行搜索，核对候选与过滤行为。
- Git：先看状态，保护用户已有修改。最终检查 diff 和新增未跟踪文件；不自动提交、不改业务代码来“配合”历史知识。
