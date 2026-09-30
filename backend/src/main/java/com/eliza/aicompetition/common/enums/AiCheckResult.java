package com.eliza.aicompetition.common.enums;

/**
 * <h1>AI 检查结果枚举（不可变）</h1>
 *
 * <p>AI 对材料内容的完整性初审结果。这是一个<b>不可变结果</b>，不是状态机。</p>
 *
 * <h2>语义</h2>
 * <ul>
 *   <li>{@link #PASSED} — AI 检查通过，材料内容无明显问题</li>
 *   <li>{@link #WARNING} — AI 检查发现风险点，建议人工关注</li>
 * </ul>
 *
 * <h2>不在本枚举中表达的状态</h2>
 * <ul>
 *   <li>未检查 → 没有 AI 检查记录</li>
 *   <li>检查中 → {@link AgentTaskStatus#RUNNING}</li>
 *   <li>调用失败 → {@link AgentTaskStatus#FAILED}</li>
 *   <li>调用超时 → {@link AgentTaskStatus#TIMEOUT}</li>
 * </ul>
 *
 * <h2>重要原则</h2>
 * <p>AI WARNING 只提供提示，不阻止学生提交教师审核。教师可以结合 AI 结果自行判断。</p>
 *
 * @see AgentTaskStatus
 */
public enum AiCheckResult {

    PASSED("AI检查通过"),
    WARNING("AI检查有风险");

    private final String label;

    AiCheckResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 从 AI 返回的原始结果字符串映射为枚举。
     *
     * @param rawResult LLM 返回的原始结果（如 "pass", "warning"）
     * @return 对应的枚举值，无法识别时返回 WARNING
     */
    public static AiCheckResult fromRawResult(String rawResult) {
        if (rawResult == null) return WARNING;
        return switch (rawResult.toLowerCase()) {
            case "pass", "passed" -> PASSED;
            case "warning", "reject" -> WARNING;
            default -> WARNING;
        };
    }
}
