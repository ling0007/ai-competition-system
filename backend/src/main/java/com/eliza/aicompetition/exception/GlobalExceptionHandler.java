package com.eliza.aicompetition.exception;

import com.eliza.aicompetition.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常 —— 根据异常的 code 字段动态设置 HTTP 状态码。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        int httpCode = exception.getCode();
        log.warn("业务异常 [code={}]: {}", httpCode, exception.getMessage());
        return ResponseEntity.status(httpCode)
            .body(ApiResponse.fail(httpCode, exception.getMessage()));
    }

    /**
     * MyBatis-Plus 乐观锁冲突异常（409 Conflict）。
     * <p>
     * 当 OptimisticLockerInnerInterceptor 校验更新行数为 0 时触发。
     * 提示用户刷新页面后重试。
     * </p>
     */
    @ExceptionHandler(com.baomidou.mybatisplus.core.exceptions.MybatisPlusException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleMybatisPlusException(
        com.baomidou.mybatisplus.core.exceptions.MybatisPlusException exception) {
        String msg = exception.getMessage();
        if (msg != null && msg.contains("optimistic")) {
            log.warn("乐观锁冲突: {}", msg);
            return ApiResponse.fail(409, "数据已被他人修改，请刷新后重试");
        }
        log.error("MyBatis-Plus 异常: {}", msg, exception);
        return ApiResponse.fail(500, "数据库操作异常: " + msg);
    }

    /**
     * 身份认证异常（401）。
     * <p>
     * 正常情况下由 JwtAuthenticationEntryPoint 在 Filter 层拦截。
     * 此处作为兜底 —— 防止少数绕过 Filter 直接进入 Controller 层的认证异常。
     * </p>
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException exception) {
        log.warn("认证异常（Controller 层兜底）: {}", exception.getMessage());
        return ApiResponse.fail(401, "未登录或登录已过期");
    }

    /**
     * 权限不足异常（403）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException exception) {
        log.warn("权限异常（Controller 层兜底）: {}", exception.getMessage());
        return ApiResponse.fail(403, "无权限访问该资源");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getDefaultMessage();
        return ApiResponse.fail(400, message);
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequestException(Exception exception) {
        return ApiResponse.fail(400, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("系统异常: {}", exception.getMessage(), exception);
        return ApiResponse.fail(500, "系统异常: " + exception.getMessage());
    }
}
