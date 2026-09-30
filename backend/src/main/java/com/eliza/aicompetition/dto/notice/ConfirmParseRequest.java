package com.eliza.aicompetition.dto.notice;

/**
 * 确认解析请求 —— 管理员确认 AI 解析结果，正式写入数据库。
 * <p>
 * 当前仅需通知 ID（从路径参数获取），无需额外字段。
 * 保留此 record 供后续扩展（如确认时附带备注）。
 * </p>
 */
public record ConfirmParseRequest() {
}
