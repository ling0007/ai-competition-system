package com.eliza.aicompetition.exception;

/**
 * 业务异常 —— 用于可预期的业务规则违反、状态冲突等场景。
 * <p>
 * 默认错误码为 400（Bad Request），可通过重载构造函数指定其他码：
 * <ul>
 *   <li>404 — 资源不存在</li>
 *   <li>409 — 状态冲突（非法流转 / 乐观锁并发冲突）</li>
 *   <li>600 — AI 服务降级</li>
 * </ul>
 * </p>
 */
public class BusinessException extends RuntimeException {

    /** HTTP 状态码，默认为 400 */
    private final int code;

    /**
     * 使用默认 400 错误码。
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /**
     * 指定 HTTP 错误码。
     *
     * @param code    HTTP 状态码
     * @param message 用户可读的中文错误信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
