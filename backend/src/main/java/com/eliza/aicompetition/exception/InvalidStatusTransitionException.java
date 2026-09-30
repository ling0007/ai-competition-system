package com.eliza.aicompetition.exception;

/**
 * <h1>非法状态流转异常</h1>
 *
 * <p>当状态机检测到不允许的状态流转时抛出。HTTP 状态码 409 Conflict。</p>
 *
 * <h2>使用示例</h2>
 * <pre>
 * throw new InvalidStatusTransitionException("ProjectStatus", currentStatus, targetStatus);
 * // → "非法状态流转：ProjectStatus DRAFT -> APPROVED"
 * </pre>
 *
 * @see BusinessException
 */
public class InvalidStatusTransitionException extends BusinessException {

    public InvalidStatusTransitionException(String stateMachine, Enum<?> from, Enum<?> to) {
        super(409, String.format(
            "非法状态流转：%s %s -> %s",
            stateMachine,
            from != null ? from.name() : "null",
            to != null ? to.name() : "null"
        ));
    }

    public InvalidStatusTransitionException(String stateMachine, String from, String to) {
        super(409, String.format(
            "非法状态流转：%s %s -> %s",
            stateMachine,
            from != null ? from : "null",
            to != null ? to : "null"
        ));
    }
}
