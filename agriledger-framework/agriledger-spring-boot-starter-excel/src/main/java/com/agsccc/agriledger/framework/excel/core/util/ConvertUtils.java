package com.agsccc.agriledger.framework.excel.core.util;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ClassUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 类型转换工具类
 *
 * @author Sail
 * @version 1.0
 * @date 2026/11/17 15:09
 */
public final class ConvertUtils {

    private ConvertUtils() {
    }

    /**
     * 将对象转换为指定类型
     *
     * @param value      原始值
     * @param targetType 目标类型
     * @return 转换结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T convert(Class<T> targetType, Object value) {
        if (targetType == null) {
            throw new IllegalArgumentException("目标类型不能为空");
        }
        if (value == null) {
            return null;
        }

        Class<?> target = ClassUtils.primitiveToWrapper(targetType);

        // 类型相同，直接返回
        if (target.isInstance(value)) {
            return (T) value;
        }

        // String
        if (target == String.class) {
            return (T) value.toString();
        }

        // Boolean
        if (target == Boolean.class) {
            Boolean result = BooleanUtils.toBooleanObject(value.toString().trim());
            if (result == null) {
                throw new IllegalArgumentException("无法转换为 Boolean: " + value);
            }
            return (T) result;
        }

        // Character
        if (target == Character.class) {
            String str = value.toString();
            if (str.length() != 1) {
                throw new IllegalArgumentException("无法转换为 Character: " + value);
            }
            return (T) Character.valueOf(str.charAt(0));
        }

        // Enum
        if (target.isEnum() && value instanceof String) {
            return (T) Enum.valueOf((Class<? extends Enum>) target, value.toString());
        }

        // 数值类型
        if (Number.class.isAssignableFrom(target)) {
            BigDecimal number;
            try {
                number = new BigDecimal(value.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无法转换为数值类型: " + value + " -> " + target.getName(), e);
            }

            if (target == Integer.class) {
                return (T) Integer.valueOf(number.intValueExact());
            }
            if (target == Long.class) {
                return (T) Long.valueOf(number.longValueExact());
            }
            if (target == Short.class) {
                return (T) Short.valueOf(number.shortValueExact());
            }
            if (target == Byte.class) {
                return (T) Byte.valueOf(number.byteValueExact());
            }
            if (target == Double.class) {
                return (T) Double.valueOf(number.doubleValue());
            }
            if (target == Float.class) {
                return (T) Float.valueOf(number.floatValue());
            }
            if (target == BigDecimal.class) {
                return (T) number;
            }
            if (target == BigInteger.class) {
                return (T) number.toBigIntegerExact();
            }
        }

        throw new IllegalArgumentException("不支持的类型转换: " + value.getClass().getName() + " -> " + targetType.getName());
    }
}
