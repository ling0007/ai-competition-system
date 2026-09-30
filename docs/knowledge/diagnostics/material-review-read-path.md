---
id: "DIAG-001"
summary: "审核显示异常时先核对版本关联与读写数据源"
role: "diagnostic"
scope: ["material", "review", "query", "mock"]
paths: ["backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java", "backend/src/main/java/com/eliza/aicompetition/service/ProjectService.java", "backend/src/main/resources/mapper/ProjectMaterialMapper.xml", "frontend/src/components/dashboard/MaterialReviewPanel.vue", "frontend/src/mock/competitionService.js"]
status: "active"
verified_at: "2026-09-10"
verified_commit: "d57f92cc6859e96c4a1afea6abe80bf733fff5ce"
---

# 审核已写入但页面仍显示未审核

## Context

项目曾将审核结果迁移到独立历史表，但查询仍读取材料表旧列，导致审核成功后列表和审核计数不变。这类问题跨越写入、版本关联、查询投影与前端展示。

## Knowledge

历史上已验证的故障机制是读写数据源分离，而不是单纯的前端刷新遗漏。DTO 仍叫 `reviewStatus` 并不能证明它来自材料表的同名旧字段。

当前核对的写路径把意见绑定到 `material_version_id`；查询先定位 requirement 的 current version，再仅关联该版本的最新审核记录。因此“查到了审核记录”还不足以解释当前页面：记录可能属于旧版本，或者页面请求走了另一条查询或 Mock 路径。历史修复只能提供优先排查假设，不能直接诊断新的故障。

## Evidence

- [材料审核闭环修复报告](../../material-review-fix-report.md) 第 1 节记录旧列读路径的根因，第 4.1 节记录按版本查询最新审核的修复。
- [MaterialService](../../../backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java)：`reviewMaterial` 写 `material_review` 的 `materialVersionId`；`updateCurrentVersion` 切换有效版本。
- [ProjectMaterialMapper.xml](../../../backend/src/main/resources/mapper/ProjectMaterialMapper.xml)：`findLatestDetailsByProjectId` 和 `findMaterialsPage` 都以 `pm.current_version_id` 关联按 `material_version_id` 选出的最新审核，并共享 ACTIVE/current uploaded version 选择语义。
- [ProjectService](../../../backend/src/main/java/com/eliza/aicompetition/service/ProjectService.java)：`approve` 消费材料查询结果作项目通过校验，所以读路径问题还会影响业务操作。
- [Mock 服务](../../../frontend/src/mock/competitionService.js) 的材料详情组装也从 `materialReviews` 按版本选择记录。Mock 的其他列表路径需独立核对，不能把详情行为外推到所有接口。
- [MaterialConsistencyIntegrationTests](../../../backend/src/test/java/com/eliza/aicompetition/MaterialConsistencyIntegrationTests.java) 的 `reviewOfVersionTwoDoesNotApplyToUploadedVersionThree` 实际验证 v2 APPROVED 后上传 v3，当前详情不显示 v2 审核；2026-09-10 在 schema 8 MySQL 上通过。

## Implications

1. 确认页面实际请求、是否使用 Mock，以及返回的材料版本 ID。
2. 核对审核写入的版本 ID、逻辑删除条件和最新记录选择规则。
3. 追踪该请求对应 Mapper 的投影、DTO、页面字段和审核计数；分别检查详情与分页。
4. 若上述数据都一致，再检查请求后的刷新、缓存与展示逻辑。重新上传后的旧版本意见不自动证明新版本已审核。

## Revisit when

调整版本选择规则、审核记录排序、读模型或缓存来源，移除旧列，或使 Mock 与真实接口共享实现时重新评估。
