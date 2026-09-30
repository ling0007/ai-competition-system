---
id: "NAV-001"
summary: "历史工程判断的查证顺序及待复核冲突"
role: "navigation"
scope: ["documentation", "security", "notice", "migration"]
paths: ["CLAUDE.md", "docs", "backend/src/main/java/com/eliza/aicompetition/config/SecurityConfig.java", "backend/src/main/java/com/eliza/aicompetition/security/SecurityUtils.java", "backend/src/main/java/com/eliza/aicompetition/service/NoticeService.java", "backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java", "backend/src/main/resources/db/migration"]
status: "needs-review"
verified_at: "2026-09-18"
verified_commit: "5c576fc + Phase 5.3 working tree"
---

# 历史工程判断的查证入口

## Context

项目演进过程中产生过设计方案、排查报告和交接记录。这些过程性资料保留在本地，不进入公开仓库，因为它们混合了目标、当时结论和一次性操作步骤；复制为另一套“当前架构”会放大历史漂移。本条只保留可公开复用的查证顺序，不保存旧任务流水。

## Knowledge

| 遇到的问题 | 先读的公开来源 | 用法 |
| --- | --- | --- |
| 产品范围与人工审核定位 | [README](../../../README.md)、[PRODUCT](../../PRODUCT.md) | 理解范围与动机；完成状态仍需回到当前代码验证 |
| 状态与材料语义 | [MODEL-001](../models/review-and-progress.md) | 先区分完整度、审核结论、项目状态和 AI 快照，再查对应代码 |
| 审核成功但展示不变 | [DIAG-001](../diagnostics/material-review-read-path.md) | 按已验证的读写链路核对版本关联与查询来源 |
| Redis 与 AI 任务边界 | [RAT-001](../decisions/redis-as-advisory-layer.md) | 先确认数据库契约，再判断缓存或短锁是否参与问题 |
| 既有工程规则 | [CLAUDE.md](../../../CLAUDE.md) | 保留原规范效力；“待完成”描述不等于当前代码事实 |

## Evidence

以下是既有文档与代码的核对线索，含仍待复核的差异；不是经批准的新约束：

| 历史说法 | 当前代码证据 | 可能原因与处理建议 |
| --- | --- | --- |
| CLAUDE §2.2 要求 `CurrentUser`；§13 将 JWT 拦截与 Redis 标为待改造 | [SecurityConfig](../../../backend/src/main/java/com/eliza/aicompetition/config/SecurityConfig.java) 已使用 JWT Filter 和 `authenticated()`；[SecurityUtils](../../../backend/src/main/java/com/eliza/aicompetition/security/SecurityUtils.java) 从安全上下文获取身份；[pom.xml](../../../backend/pom.xml) 已含 Redis 依赖 | 可能是实施后规范未同步。进度文字可核对后修正；`CurrentUser` 的强制命名规则是否接受现有实现须人工批准，不能静默视作等价规则 |
| 历史方案曾称 AI 调用已移出事务；CLAUDE §2.3 要求 AI 调用方法本身不加事务 | [NoticeService](../../../backend/src/main/java/com/eliza/aicompetition/service/NoticeService.java) 现在由 `acceptParseTask` 短事务接受 `PENDING`，`executeParseTask` 在事务外执行提取/OCR/模型，只在领取与结果提交时使用短事务；旧同步入口已移除 | Phase 5.3 将原同步短事务边界扩展为后台任务；`NoticeAsyncIntegrationTests` 用隔离 MySQL 验证了领取、晚到结果与故障恢复，`NoticeLifecycleIntegrationTests` 继续验证人工确认/发布链 |
| CLAUDE §3.3 与 §5.3 的教师资源归属要求；本行旧版曾记录全局教师放行的 FIXME | 2026-09-28 核对：[MaterialService.reviewMaterial](../../../backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java) 先校验角色再调用 [ProjectService.checkProjectReviewAccess](../../../backend/src/main/java/com/eliza/aicompetition/service/ProjectService.java)，后者要求教师具有该项目 advisor 成员关系，ADMIN 可访问；[资源授权测试](../../../backend/src/test/java/com/eliza/aicompetition/ResourceAuthorizationIntegrationTests.java) 有对应覆盖 | 旧线索已被后续授权实现取代；Git 历史 `c21cef5`（2026-09-17）引入该调用链。本项当前代码与规范一致，不再把旧 FIXME 当成现存漏洞。其他行仍需按各自条件复核。 |
| 历史排查曾建议清理 V7 残留列与失败记录后重试 | [现有迁移文件](../../../backend/src/main/resources/db/migration/V7__refactor_status_model.sql) 无法证明某个实际数据库的迁移历史与残留状态；CLAUDE §4.1 禁止生产手工 SQL | 该建议来自一次开发环境故障，不能作为通用恢复手册；必须先核定目标环境、Flyway 历史和备份方案 |

核对基线见 frontmatter；未连接实际数据库、未执行旧文档中的修复 SQL。

## Implications

只打开与任务相关的来源，再回到对应代码、测试和 Git 历史。对照冲突表向用户明确报告 constraint 相关差异；事实核验不能被当成修改既有规范的授权。建议尚未批准时保留 `needs-review`，也不通过其他 role 写入相同的限制。

## Revisit when

上述规则得到人工澄清、AI 事务获得运行验证、旧文档完成修订或迁移恢复方案得到验证时，更新对应行和证据；差异全部解决后再评估本导航的 status。
