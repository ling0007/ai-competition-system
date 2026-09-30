---
id: "MODEL-001"
summary: "完整度、人工审核、项目状态与 AI 输入快照各回答不同问题"
role: "model"
scope: ["project", "material", "review", "progress"]
paths: ["backend/src/main/java/com/eliza/aicompetition/service/ProjectService.java", "backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java", "backend/src/main/java/com/eliza/aicompetition/service/AgentService.java", "backend/src/main/resources/mapper/ProjectMaterialMapper.xml", "backend/src/main/resources/db/migration/V7__refactor_status_model.sql", "frontend/src/components/dashboard/MaterialCheckPanel.vue", "frontend/src/views/student/ProjectWorkspaceView.vue"]
status: "active"
verified_at: "2026-09-25"
verified_commit: "7f5ff768eb49204559f99e5a530491455da527e5"
---

# 材料完整度、单项审核与项目结论

## Context

历史状态模型把材料缺失、AI 意见和项目生命周期混在一起。上传后仍是草稿、单项材料审核后项目仍待审，容易被当作同一种状态同步故障。

## Knowledge

拆分这些维度的设计理由是消除重复状态的双写风险，并保留每次审核的历史。完整度回答“必交材料有没有当前版本”，单项审核回答“某个版本得到什么人工意见”，项目生命周期回答“申报流程走到哪里”。材料齐全本身并不表达已经提交或项目获批。

AI 检查还回答第四个问题：“某一次项目状态、ACTIVE 材料要求和当前材料版本快照上发现了什么”。Phase 5.4 的 v2 快照记录项目上下文、所有 ACTIVE 要求的 ID/必交标记/当前版本 ID 清单及其语义指纹，并列出已上传版本 ID；当前要求集合、必交属性或版本变化后，即使完整度仍为 100%，旧 AI 结果也必须标记为过期。早期只记录版本 ID 的快照无法证明要求集合未变，因此保守标记过期。历史版本、历史审核和历史 AI 结果都用于追溯，不反向推导 current version 状态。

schema 8 仍保留数据库存储过程/触发器，但其统计按 `project_material` 行数计算，不能表达 ACTIVE requirement 与 current/latest uploaded version 语义。应用层因此以 `ProjectService.refreshProjectProgress` 为唯一业务算法：Mapper 只返回 ACTIVE requirement 及其当前已上传版本，Service 按必交 requirement 计数并覆盖 `completion_rate` 展示缓存。触发器是兼容保留的非权威写入，应用不得读取其过程结果作为 submit、resubmit 或页面完整性的依据。

## Evidence

- [状态模型重构交接](../../状态模型重构_任务交接.md) 的“与改造方案的主要差异”说明消除双写、将审核改为历史记录的理由。
- [ProjectService](../../../backend/src/main/java/com/eliza/aicompetition/service/ProjectService.java)：`findCurrentMaterialViews`、`refreshProjectProgress`、`submitForReview` 和 `resubmit` 统一当前材料读模型、完整度与提交校验。
- [ProjectMaterialMapper.xml](../../../backend/src/main/resources/mapper/ProjectMaterialMapper.xml)：只取 ACTIVE requirement，并用自指 `current_version_id = material_id` 的已上传行定位当前版本。
- [MaterialService](../../../backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java)：`reviewMaterial` 插入版本审核记录；[V7](../../../backend/src/main/resources/db/migration/V7__refactor_status_model.sql) 的 `sp_refresh_project_progress` 与两个材料触发器揭示另一条进度更新路径。
- [AgentService](../../../backend/src/main/java/com/eliza/aicompetition/service/AgentService.java) 的 `listProjectChecks` 对比当前 v2 输入快照与历史快照；[学生项目工作区](../../../frontend/src/views/student/ProjectWorkspaceView.vue) 明确展示检查时间、版本和过期状态。
- 2026-09-25 Phase 5.4 当次工作区（基于 `7f5ff768eb49204559f99e5a530491455da527e5`）：`AgentService.snapshot` 对项目上下文与 ACTIVE 要求形成 v2 指纹，`executeMaterialTask` 最终事务重核；材料上传事务和 AI 提交事务在改版本/写结论前锁同一项目与通知行。`AgentSyncTaskContractIntegrationTests` 在隔离 MySQL 上验证版本、要求、项目状态变化时不提交旧结论或风险消息，全量 121 项中 118 项执行、3 项 opt-in 跳过，0 失败/错误。
- [MaterialConsistencyIntegrationTests](../../../backend/src/test/java/com/eliza/aicompetition/MaterialConsistencyIntegrationTests.java) 在 schema 8 MySQL 上验证 ACTIVE 过滤、版本去重、提交边界、审核版本隔离、版本历史及 AI 快照过期；2026-09-18 全量后端测试 79 项通过。

## Implications

遇到“进度 100% 但项目没通过”，先确认用户预期的是完整度、版本审核、AI 快照有效性还是项目动作，再沿对应入口追踪。调整完整度时优先检查 Mapper 当前版本取数与 Service 派生算法；数据库触发器的瞬时结果不应作为业务证据。调整审核展示时接着查 [审核读写链路](../diagnostics/material-review-read-path.md)；AI 结果看似矛盾时先比较快照版本集合与当前版本集合。

## Revisit when

引入独立提交批次、材料版本规则或 AI 快照格式改变、替换数据库触发器、改变项目人工审核流程，或修改完整度统计口径时重新核对。v2 快照格式改动后还须重新评估旧记录的保守 stale 策略。
