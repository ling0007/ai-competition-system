---
id: "replace-with-stable-id"
summary: "一句话说明这条判断何时有用，而非复述实现"
role: "model"
scope: ["领域概念", "模块"]
paths: ["仓库相对路径/到文件或目录"]
status: "needs-review"
verified_at: "YYYY-MM-DD"
verified_commit: "核对证据时的完整 commit hash"
---

# 标题

## Context

这个知识为什么存在；反复出现的决策或排查问题是什么。

## Knowledge

可以独立复用的工程判断。区分已验证结论、推断与适用范围，不写任务流水账或代码清单。

## Evidence

- 使用相对 Markdown 链接指向代码、测试、已有设计文档；同时写明 symbol 或章节。
- 记录实际核对的 commit、测试或任务结果，区分静态检查与实际执行。
- 不把测试文件存在写成测试通过，不把计划中的方案写成已实现事实。

## Implications

未来遇到什么情况，优先查哪里、验证什么假设、如何调整设计判断。

## Revisit when

列出使结论可能失效的具体变化，例如持久化模型、版本语义或事务入口变化。

<!-- 模板说明：复制时删除此注释。
role 仅允许 navigation / model / playbook / diagnostic / rationale / constraint。
status 仅使用 active / needs-review / deprecated；active 仍须在使用前核对代码。
目录对应 navigation / models / playbooks / diagnostics / decisions / constraints。
frontmatter 使用每行一个键；字符串用 JSON 双引号；scope、paths 用单行 JSON 字符串数组。
这是合法 YAML 的小子集，便于 search.py 用标准库读取；不支持多行值、嵌套 YAML 或别名。
paths 使用仓库相对路径与正斜杠；知识 id 稳定，文件移动时保持 id 并更新索引与链接。
verified_* 是证据核对基线，不表示基线以后仍成立；未实际验证时保持 needs-review 并说明缺口。
constraint 仅在人工明确批准具体内容后才能创建，记录可追溯的批准依据。
不能通过使用 model/rationale 等其他 role 绕过 constraint 审批。
先评估 NO CHANGE > UPDATE EXISTING > CREATE NEW，更新正文时同步索引元数据。
-->
