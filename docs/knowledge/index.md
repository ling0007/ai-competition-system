# Project Knowledge System

这里是历史工程判断的地图。当前代码、测试和配置才是当前事实的最高权威；召回知识后，重要判断仍需对照当前实现与 Git 历史验证。

## 使用说明

1. 先定位任务涉及的领域、模块、路径和 symbol，再从本索引选择最多 5 条真正相关的知识。
2. 只读相关条目。知识用于缩小探索空间，不能替代代码阅读；`active` 也不是永久有效的事实认证。
3. 冲突要明确写出历史说法、当前证据、可能变化原因和更新建议。`constraint` 冲突报告用户；`needs-review` 仅作为探索线索。
4. 收尾按 [AGENTS.md](../../AGENTS.md) 的 Knowledge Writeback Evaluation 判断。默认 **NO CHANGE > UPDATE EXISTING > CREATE NEW**，不保存聊天、任务总结、临时想法或易从代码恢复的信息。
5. 使用 [TEMPLATE.md](TEMPLATE.md)；先找同类知识，优先修正已有正文。变更时同步本表的 id、summary、role、scope、路径和 status。`deprecated` 保留可追溯关系，不进入默认检索。

## 知识角色

| role | 目录 | 记录什么 |
| --- | --- | --- |
| navigation | navigation/ | 跨文档、模块的探索入口和查证顺序 |
| model | models/ | 单个文件难以表达的系统关系与概念区别 |
| playbook | playbooks/ | 已验证、可复用且有适用条件的操作方法 |
| diagnostic | diagnostics/ | 已验证的故障机制及优先排查路径 |
| rationale | decisions/ | 有证据的设计理由、权衡及重评条件 |
| constraint | constraints/ | 直接限制解决方案空间的工程边界；具体内容须人工批准 |

目录可以为空，通过 `.gitkeep` 保持版本控制。模板与本索引不算知识对象。没有新建 constraint；现有规范仍由 [CLAUDE.md](../../CLAUDE.md) 保留，不因尚未转成知识对象而失效。

## 知识对象

| id | summary | role | scope | 文件 | status |
| --- | --- | --- | --- | --- | --- |
| NAV-001 | 历史工程判断的查证顺序及待复核冲突 | navigation | documentation, security, notice, migration | [历史判断导航](navigation/existing-documents.md) | needs-review |
| MODEL-001 | 完整度、人工审核、项目状态与 AI 输入快照各回答不同问题 | model | project, material, review, progress | [审核与进度关系](models/review-and-progress.md) | active |
| RAT-001 | AI 任务以 DB 契约保唯一性，Redis 只减上传竞争并缓存非权限数据 | rationale | redis, ai-task, material, dashboard | [Redis 可降级职责](decisions/redis-as-advisory-layer.md) | active |
| DIAG-001 | 审核显示异常时先核对版本关联与读写数据源 | diagnostic | material, review, query, mock | [审核读写链路](diagnostics/material-review-read-path.md) | active |

## 简单检索

在仓库根目录运行，使用 Python 3 标准库，无需安装依赖：

```text
python scripts/knowledge/search.py 审核 版本
python scripts/knowledge/search.py --path backend/src/main/java/com/eliza/aicompetition/service/MaterialService.java
python scripts/knowledge/search.py --scope material --role diagnostic
```

工具只读取 frontmatter 和首个一级标题，不搜索正文；默认最多返回 5 条，显示 status 与路径。词项按 OR 匹配并排序，`--scope` / `--path` / `--role` 之间按 AND 过滤，同类重复选项按 OR 匹配。支持 `--include-deprecated` 人工追溯。无匹配并不表示没有历史背景，回到代码探索，不自动写新知识。

没有 Python 时，先搜索引元数据，再按需打开候选文件：

```text
rg -n "审核|版本|material" docs/knowledge/index.md
rg -n "^(id|summary|role|scope|paths|status):|^# " docs/knowledge/navigation docs/knowledge/models docs/knowledge/playbooks docs/knowledge/diagnostics docs/knowledge/decisions docs/knowledge/constraints
```
