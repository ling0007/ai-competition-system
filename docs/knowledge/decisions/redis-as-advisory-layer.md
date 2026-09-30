---
id: "RAT-001"
summary: "AI 任务以 DB 契约保唯一性，Redis 只减上传竞争并缓存非权限数据"
role: "rationale"
scope: ["redis", "ai-task", "material", "dashboard"]
paths: ["backend/src/main/java/com/eliza/aicompetition/service/RedisLockService.java", "backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java", "backend/src/main/java/com/eliza/aicompetition/service/DashboardCacheService.java", "backend/src/main/java/com/eliza/aicompetition/controller/DashboardController.java"]
status: "active"
verified_at: "2026-09-25"
verified_commit: "7f5ff768eb49204559f99e5a530491455da527e5"
---

# Redis 仅承担可降级的减竞争和缓存职责

## Context

异步 AI 任务可运行数分钟，而固定 TTL 的 Redis 锁会过期、失联或被旧持有者错误释放。Dashboard 原先把项目权限、进度和通知一起缓存，使失效范围与正确性边界混在一起。

## Knowledge

通知和材料 AI 的活跃任务唯一键、worker 领取 CAS、终态保护与输入快照已经在数据库中形成完整契约；长时间 Redis 锁没有额外的最终正确性作用。上传的短事务仍可用 Redis 锁快速挡住重复请求，但项目/通知行锁及同版本唯一键必须独立守住结果。锁只能持有到真实事务完成，调用方法的 `finally` 不一定晚于 Spring 代理的提交。

Dashboard 的项目、成员、进度、审核状态和 AI 当前性涉及授权或即时业务决策，读取时重新查 DB。通知和用户选项是全局展示数据，可按用户缓存，并用提交后递增的全局 epoch 逻辑失效；这样不枚举用户 key。失效失败仍可能留下最长 TTL 的全局旧值，因此这层缓存不能承载权限决定。

## Evidence

- [AI 任务和通知代码](../../../backend/src/main/java/com/eliza/aicompetition/service/NoticeService.java)、[材料 AI 代码](../../../backend/src/main/java/com/eliza/aicompetition/service/AgentService.java) 与 [V10](../../../backend/src/main/resources/db/migration/V10__agent_task_active_business_guard.sql)：活跃任务唯一键、CAS、快照和终态检查；AI 接受与执行不再使用 Redis 锁。
- [上传事务](../../../backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java) 的 `uploadMaterial` 使用 `TransactionTemplate`，外层事务存在时通过 `afterCompletion` 释放；[RedisLockService](../../../backend/src/main/java/com/eliza/aicompetition/service/RedisLockService.java) 原子获取 owner token 并用 Lua compare-and-delete。
- [DashboardCacheService](../../../backend/src/main/java/com/eliza/aicompetition/service/DashboardCacheService.java) 与 [DashboardController](../../../backend/src/main/java/com/eliza/aicompetition/controller/DashboardController.java)：全局字段带 role/epoch，项目字段每次查库；失效注册 `afterCommit`。
- 2026-09-25 当次工作区（基于上述 commit）：独立 MySQL 至 V12 与临时 Redis 7.2 的全量测试 135 项中 132 项执行、3 项 opt-in 跳过，0 失败/错误。[真实 Redis 测试](../../../backend/src/test/java/com/eliza/aicompetition/RedisOwnershipIntegrationTests.java) 覆盖 owner、TTL、Lua 和缓存 epoch；[事务集成测试](../../../backend/src/test/java/com/eliza/aicompetition/AgentSyncTaskContractIntegrationTests.java) 覆盖提交后解锁、Redis 故障并发上传、写入后失效与回滚。
- Phase 5.6 新隔离库全量后端 144 项中 137 项执行、7 项 opt-in 跳过，0 失败/错误。[Redis 故障集成测试](../../../backend/src/test/java/com/eliza/aicompetition/Phase56RedisOutageIntegrationTests.java) 用不可用 Redis 验证 Dashboard 回退 DB、并发上传按版本顺序完成且 current version 唯一、成员移除立即生效；[epoch 测试](../../../backend/src/test/java/com/eliza/aicompetition/Phase56DashboardEpochIntegrationTests.java) 验证提交推进而回滚不推进；真实 Redis owner/TTL/Lua 与失效失败后 TTL 消退另由 `RedisOwnershipIntegrationTests` 验证。此证据只覆盖隔离环境，不推断实际缓存命中率。

## Implications

新增 Redis 用途时先找数据库最终保护与失败语义，再决定是否值得缓存或快速锁。若 Dashboard 出现旧项目或权限数据，优先检查查询是否绕过当前成员/项目授权；若只有通知或用户选项陈旧，再检查 epoch 递增失败日志和 TTL。上传锁故障不等同于可重复写成功，需检查行锁和版本约束的实际事务边界。

## Revisit when

AI Task Contract、材料上传的事务入口或唯一键变化，Dashboard 返回新字段或引入实际共享热点，多实例部署改变一致性要求，或真实流量证明全局 epoch 及短 TTL 无法满足时重新评估。
