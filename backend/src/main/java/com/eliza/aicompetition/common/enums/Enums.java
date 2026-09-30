package com.eliza.aicompetition.common.enums;

import com.eliza.aicompetition.exception.BusinessException;

/**
 * 枚举工具类 —— 提供安全的枚举值转换方法。
 */
public final class Enums {

    private Enums() {
        // utility class
    }

    /**
     * 安全的枚举值转换，非法值抛出带有中文提示的 BusinessException。
     *
     * @param enumClass 枚举类
     * @param value     字符串值
     * @param fieldName 字段名（用于错误提示）
     * @param <E>       枚举类型
     * @return 枚举实例
     * @throws BusinessException 如果值为 null 或无法匹配
     */
    public static <E extends Enum<E>> E safeValueOf(Class<E> enumClass, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "无效的" + fieldName + ": " + value);
        }
    }
}
