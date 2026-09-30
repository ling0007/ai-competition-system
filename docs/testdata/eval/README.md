# Phase 6.1 通知解析 Eval

`notice-gold-v1.json` 来自三份原始文件逐页/逐段人工核对，SHA-256 在每次运行前校验。`expectedVersion` 是评分口径版本；单样本 `sampleVersion` 对应文件哈希。修改原文件或评分范围时，应重新人工核对、更新哈希与版本，不能按模型输出修订 gold。

这些样本用于开发期回归，其中扫描 PDF 曾参与 Prompt tuning 和错误分析。当前报告能证明固定样本上的评分、提取路由、页覆盖及历史问题回归，不能证明对未知通知的独立泛化准确率。需要更严格评估时，应在 Prompt/合并规则冻结后增加从未参与调参的 holdout 样本，并避免根据 holdout 结果继续调 Prompt。

## 范围与规则

- TXT：全文的两项初次申报必交材料。文字 PDF：报名回执及报到时明确要求提交/出示的七项资料；第 4 页落款不等于明确的主办单位，因此 `organizer` 不评分。扫描 PDF：报名与**区域赛初赛**的三项材料；第 7 页还列出复赛/决赛的不同规则，当前 DTO 没有赛段字段，不能把它们混成一组必交布尔值。Phase 6.1 baseline 当时只 OCR 扫描件前 3/10 页，完整 gold 中第 5 页截止时间和第 7 页材料是有意保留的基线漏检项；Phase 6.3 收尾报告已覆盖 10/10 页。
- 标题、主办方、材料名称只做 Unicode NFKC 和 Unicode 空白删除，再与 gold 正式值或显式 `aliases` 精确相等比较；不做包含、编辑距离、同义词、分词或 LLM 判分。别名须有人工依据。无法确定字段不进入该字段分母。
- 截止时间只接受严格有效的 `yyyy-MM-dd HH:mm` 或 `yyyy-MM-dd HH:mm:ss`，按日期和时分完整比较。文字 PDF 的“7月10日前”没有时分，gold 按当前解析 Prompt 的纯日期 `23:59` 规则记录；年份由该文件标题的 2023 年确定。`18:00` 变成 `23:59` 视为错误，非法日期与错误时分分开报告。
- 模型原始内容必须是 JSON 对象；检查六个字段是否存在、标量字段是字符串/null、`materials` 是数组、每项有非空 `name`、字符串/null `description` 和布尔 `isRequired`。即使生产 `AiService.cleanJson` 容忍代码围栏，原始 JSON 合法率仍按原始模型输出计算。无有效响应的 `FALLBACK/NONE` 不混入 `MODEL` 质量分母。
- 材料按名称/显式别名一对一匹配；重复命中计一个 TP 和一个 FP，漏项为 FN，额外项为 FP。`precision=TP/(TP+FP)`、`recall=TP/(TP+FN)`、`F1=2PR/(P+R)`；必交准确率为匹配项中标记正确数/匹配项数，没有匹配项时为 null。对范围外的后续赛段材料，报告可能列为额外项；这反映当前 DTO 缺少赛段信息，不自动改 gold。

## 运行

在 `backend/` 下运行：

```powershell
.\mvnw.cmd "-Dtest=NoticeEvalScorerTests,NoticeEvalExtractionTests" test
```

该命令不调用 DashScope、不需要数据库，生成 `target/ai-eval/offline-example.json`，其 `exampleType` 明确标为合成离线样例。

真实运行先在当前 PowerShell 进程中配置 `DASHSCOPE_API_KEY`，然后执行：

```powershell
$env:DASHSCOPE_API_KEY = [Environment]::GetEnvironmentVariable('DASHSCOPE_API_KEY', 'User')
.\mvnw.cmd "-Dtest=NoticeRealEvalTests" "-Dphase6.eval.real=true" test
```

结果写到 `backend/target/ai-eval/notice-report.json`，可用 `-Dphase6.eval.report=...` 改输出位置。入口直接读取 gold 指定的原始文件字节，经现有 `FileTextExtractor` 的 Tika→必要时 OCR 链路，再调用现有 `AiService`；不写通知 `rawText`，不建 DB 任务，不产生草稿。报告仅保存样本 ID/哈希、字段通过或失败原因、材料差错清单及已有阶段耗时，不保存 Key、全文或原始模型响应。它是**组件级真实模型 Eval**，不含异步排队、HTTP 接受或浏览器可见耗时；任务层观测留给 6.2。

真实请求可能收费且受外部服务状态影响。若 Key 缺失，opt-in 测试会明确失败；默认离线命令不会触发真实请求。此目录不是评测平台，也不作为生产发布门禁。

2026-09-26 的一次真实 `qwen3.7-flash` 运行结果保存为[精简报告](../../plans/phase61-real-eval-20260926.json)：三份样本均为 `MODEL`，原始 JSON 与结构均合法；标题 2/3，主办方 2/2（文字 PDF 不计），截止时间 2/3。文字 PDF 的“综合意外险保单”没有预声明为“保险单”别名，因此严格评分记一个 FP 和一个 FN；不因这次模型输出反向修改 gold。扫描 PDF 的截止时间、区域赛初赛材料在当前 OCR 页数之外，材料召回为 0/3。本次样本数很小，不推断总体精度。

2026-09-27 对同一 SHA-256 扫描原文件的[收尾复测报告](../../plans/phase6-closeout-real-eval-20260927.json)覆盖 10/10 页，`visionCalls=10`、`mainModelCalls=2`。长文本按页分段，评分输入是确定性合并后的 DTO（`scoreSource=MERGED_RESULT`），因此报告中的 `jsonValid/schemaValid` 指合并结果，不能解读为每个原始 chunk 响应均通过严格原始 JSON 检查。可用 `-Dphase6.eval.sampleId=software-contest-scanned-pdf` 只重跑扫描样本。该次 title/organizer/deadline 均通过；材料 TP/FP/FN=3/1/0，precision=0.75、recall=1.00、必交准确率=1.00。唯一 FP 是泛称“参赛作品”；当前策略优先保证明确材料不漏召回，草稿仍由人工确认，不为单一样本增加复杂排除规则。报告是一次组件级开发集回归，不含异步排队和真实任务提交。
