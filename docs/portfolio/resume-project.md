# 简历项目描述（Java 后端 / AI 应用工程师实习）

**AI 材料申报与审核平台**｜面向高校竞赛申报的 Spring Boot + Vue 系统，覆盖通知解析、材料提交、AI 初审与人工复核。

- 围绕通知发布、项目申报和多版本材料审核，使用 Spring Boot、MyBatis-Plus、MySQL/Flyway 与 Spring Security/JWT 实现角色和资源权限、状态流转及可追溯审核记录；AI 结果须经管理员或教师人工确认。
- 针对 OCR/模型调用使请求长时间等待的问题，设计 MySQL 持久化异步任务：请求返回 `taskId`，有界线程池执行，CAS 领取、定时恢复、deadline 和输入快照阻止重复执行与晚到结果污染；Redis 仅做可降级缓存和上传短锁。
- 针对扫描 PDF 后页信息丢失，构建 Tika 优先、PDFBox 分页渲染与视觉 OCR 的有界链路，并按页分段解析；已知 10 页样本 OCR 覆盖 10/10 页，材料 Eval 由前 3 页基线 TP/FP/FN=`0/2/3` 提升至 `3/1/0`（precision `0.75`、recall `1.00`）。
- 用三份带 SHA-256 的 gold 和确定性评分区分模型成功、字段正确与 fallback；配置 Docker Compose 管理 MySQL/Redis/应用，已在空库实测 Flyway 至 V13、健康接口、JWT 接口和无 Key 降级路径。

**使用边界**：Eval 数字来自参与过 Prompt 调整的已知样本单次组件级回归，不代表未知通知的准确率；当前环境尚未验证完整 Compose 应用镜像构建。

证据：[异步任务](../interview/05-ai-async-task.md) · [PDF/OCR](../interview/07-pdf-ocr-long-document.md) · [Eval 报告](../plans/phase6-closeout-real-eval-20260927.json) · [Compose 复现](../interview/08-local-compose-reproducibility.md)
